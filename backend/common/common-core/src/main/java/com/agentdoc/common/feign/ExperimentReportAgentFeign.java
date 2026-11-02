package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.vo.AgentCandidateConfigVO;
import com.agentdoc.common.utils.ReportProofSignature;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/** 首次报告的候选证明校验，直连 Agent 内部入口。 */
@FeignClient(name = "experiment-report-agent", url = "${agent-doc.online.agent-url:http://localhost:8084}")
public interface ExperimentReportAgentFeign {
    @GetMapping("/api/agent/internal/experiment-report/candidate-configs/{id}")
    Result<AgentCandidateConfigVO> identity(@PathVariable Long id, @RequestParam Long spaceId,
            @RequestParam String candidateSnapshotHash, @RequestHeader(ReportProofSignature.HEADER) String proof);
}
