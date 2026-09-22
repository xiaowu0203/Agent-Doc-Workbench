# ADR-0004：Execution Snapshot v3 规范化协议

## 状态

已接受（2026-09-16）

## 决策

Execution Snapshot v3 使用固定 envelope：`{"schemaVersion":3,"snapshot":...}`，并按以下规则生成 UTF-8 JSON 与 SHA-256：

1. 对象 key 按其 UTF-8 编码的无符号字节序升序排列，不依赖 Locale、数据库 collation 或 JVM UTF-16 自然序。
2. 数组只按对应 record 声明的领域稳定键排序；有业务顺序的数组保持原顺序。
3. null、空数组和空对象全部保留且互不等价；输出无额外空白。
4. 协议禁止二进制浮点、NaN、Infinity 和指数格式。十进制先校验字段范围与最大 scale，再以 `stripTrailingZeros().toPlainString()` 输出，零统一为 `"0"`。
5. 完整 v3 只允许 Runtime 恢复路径和受审计的诊断入口读取；普通 API 只暴露 schema version、hash、replayable 和原因码。
6. v1/v2 继续按原协议解释，不使用 v3 规则重新计算旧 hash。任何字段集合或规则变化都必须升级 schema。

## 验证

每个 schema 永久保留 canonical JSON、hash、反序列化再哈希、Unicode key、小数和篡改检测 golden fixture。
