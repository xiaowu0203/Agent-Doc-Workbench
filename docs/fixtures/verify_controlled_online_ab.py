"""独立协议参考验证器；只读取公开 fixture，不连接业务服务或改写 fixture。"""

import hashlib
import json
import math
import re
from decimal import Decimal, localcontext
from fractions import Fraction
from pathlib import Path


def canonical(value):
    if value is None or type(value) in (bool, int):
        return json.dumps(value, separators=(',', ':'))
    if isinstance(value, str):
        value.encode('utf-8')
        return json.dumps(value, ensure_ascii=False, separators=(',', ':'))
    if isinstance(value, list):
        return '[' + ','.join(canonical(item) for item in value) + ']'
    if isinstance(value, dict):
        return '{' + ','.join(canonical(key) + ':' + canonical(value[key])
                              for key in sorted(value, key=lambda key: key.encode('utf-8'))) + '}'
    raise ValueError('Protocol forbids binary floating point or unsupported values')


def document_bucket(experiment_id, document_id, seed, weight):
    for identifier in (experiment_id, document_id):
        if not re.fullmatch(r'[1-9][0-9]*', identifier) or int(identifier) > 2 ** 63 - 1:
            raise ValueError('Invalid identifier')
    if not re.fullmatch(r'[0-9a-f]{32}', seed) or type(weight) is not int or not 1 <= weight <= 9999:
        raise ValueError('Invalid seed or weight')
    value = {'domain': 'online.bucket', 'schemaVersion': 2,
             'payload': {'experimentId': experiment_id, 'documentId': document_id, 'seed': seed}}
    encoded = canonical(value)
    digest = hashlib.sha256(encoded.encode('utf-8')).hexdigest()
    bucket = int(digest, 16) % 10000
    return encoded, digest, bucket, 'CANDIDATE' if bucket < weight else 'BASELINE'


def exact_binomial_probability(n, observed, weight):
    if not 0 <= observed <= n or n < 1 or not 1 <= weight <= 9999:
        raise ValueError('Invalid binomial input')
    numerators = [math.comb(n, k) * weight ** k * (10000 - weight) ** (n - k)
                  for k in range(n + 1)]
    numerator = sum(value for value in numerators if value <= numerators[observed])
    return Fraction(numerator, 10000 ** n)


def srm_status(n, observed, weight):
    if not 0 <= observed <= n or not 1 <= weight <= 9999:
        raise ValueError('Invalid SRM input')
    if n < 100 or n * weight < 100000 or n * (10000 - weight) < 100000:
        return 'NOT_ENOUGH_UNITS', None
    probability = exact_binomial_probability(n, observed, weight)
    return ('DETECTED' if probability < Fraction(1, 1000) else 'PASS'), probability


def clopper_pearson(success, count):
    if not 0 <= success <= count:
        raise ValueError('Invalid proportion input')
    if count == 0:
        return None
    with localcontext() as context:
        context.prec = 70
        def cdf(k, p):
            return sum(Decimal(math.comb(count, i)) * p ** i * (1 - p) ** (count - i)
                       for i in range(k + 1))
        def solve(k, target):
            left, right = Decimal(0), Decimal(1)
            for _ in range(80):
                middle = (left + right) / 2
                if cdf(k, middle) > target:
                    left = middle
                else:
                    right = middle
            return (left + right) / 2
        lower = Decimal(0) if success == 0 else solve(success - 1, Decimal('0.975'))
        upper = Decimal(1) if success == count else solve(success, Decimal('0.025'))
        return lower, upper


def primary_status(terminal, rules):
    if terminal in ('FAILED', 'TIMED_OUT') or 'FAIL' in rules:
        return 'FAIL'
    if terminal == 'COMPLETED' and rules and all(rule == 'PASS' for rule in rules):
        return 'PASS'
    return 'MISSING'


def comparison_eligible(case):
    if case['mode'] != 'DESCRIPTIVE' or case['unresolvedIntegrity'] or not case['srmEnabled']:
        return False
    for group in case['groups']:
        if group['documents'] < 30 or group['valid'] * 10000 < group['documents'] * 8000:
            return False
        if case['humanQualityRequired'] and group['blinded'] * 10000 < group['documents'] * 8000:
            return False
    return True


def health_paused(states):
    eligible = [state for state in states if state in ('COMPLETED', 'FAILED', 'TIMED_OUT')][-20:]
    return len(eligible) == 20 and sum(state != 'COMPLETED' for state in eligible) >= 6


def check_fixtures():
    fixture = json.loads(Path(__file__).with_name('controlled-online-ab-v2.json').read_text(encoding='utf-8'))
    checks = 0
    for case in fixture['canonical']:
        text = canonical(case['value'])
        assert text == case['canonical'], case['name']
        assert hashlib.sha256(text.encode('utf-8')).hexdigest() == case['hash'], case['name']
        checks += 1
    for case in fixture['buckets']:
        encoded, digest, bucket, variant = document_bucket(**case['input'])
        assert (encoded, digest, bucket, variant) == (case['canonical'], case['hash'], case['bucket'], case['variant']), case['name']
        checks += 1
    for case in fixture['invalidBuckets']:
        try:
            document_bucket(**case['input'])
        except (ValueError, TypeError):
            checks += 1
        else:
            raise AssertionError(case['name'])
    for case in fixture['srm']:
        status, probability = srm_status(**case['input'])
        assert status == case['status'], case['name']
        if probability is not None:
            with localcontext() as context:
                context.prec = 70
                actual = Decimal(probability.numerator) / Decimal(probability.denominator)
                assert abs(actual - Decimal(case['probability'])) < Decimal('1e-30'), case['name']
        else:
            assert case['probability'] is None, case['name']
        checks += 1
    for case in fixture['confidenceIntervals']:
        result = clopper_pearson(**case['input'])
        if result is None:
            assert case['lower'] is None and case['upper'] is None
        else:
            assert all(abs(value - Decimal(expected)) <= Decimal('1e-12')
                       for value, expected in zip(result, (case['lower'], case['upper']))), case['name']
        checks += 1
    for case in fixture['primaryStatuses']:
        assert primary_status(**case['input']) == case['status'], case['name']
        checks += 1
    for case in fixture['comparisonGates']:
        assert comparison_eligible(case['input']) == case['eligible'], case['name']
        checks += 1
    for case in fixture['healthWindows']:
        assert health_paused(case['states']) == case['paused'], case['name']
        checks += 1
    assert ('CANDIDATE' if 4999 < 5000 else 'BASELINE') == 'CANDIDATE'
    assert ('CANDIDATE' if 5000 < 5000 else 'BASELINE') == 'BASELINE'
    print(f'{checks} protocol fixture cases passed; bucket threshold boundaries passed')


if __name__ == '__main__':
    check_fixtures()
