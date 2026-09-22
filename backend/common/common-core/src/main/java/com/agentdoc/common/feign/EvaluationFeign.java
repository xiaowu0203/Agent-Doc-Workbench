package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.MetricComparisonQueryDTO;
import com.agentdoc.common.feign.vo.MetricComparisonInputVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Evaluation Core 对外提供的稳定比较输入契约。 */
@FeignClient(name = "evaluation-service", url = "${agent-doc.feign.gateway-url:http://localhost:9090}")
public interface EvaluationFeign {

    /** 获取仅包含当前有效标准 Metric 的对比输入。 */
    @PostMapping("/api/evaluation/metrics/compare-input")
    Result<MetricComparisonInputVO> getMetricComparisonInput(@RequestBody MetricComparisonQueryDTO request);
}
