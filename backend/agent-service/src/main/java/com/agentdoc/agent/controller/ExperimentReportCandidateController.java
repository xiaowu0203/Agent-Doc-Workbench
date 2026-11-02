package com.agentdoc.agent.controller;

import com.agentdoc.agent.service.AgentCandidateConfigService;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.vo.AgentCandidateConfigVO;
import com.agentdoc.common.utils.ReportProofSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** 后台报告专用的候选配置证明校验。 */
@RestController
public class ExperimentReportCandidateController {
    private final AgentCandidateConfigService candidateConfigService;
    private final String key;

    public ExperimentReportCandidateController(AgentCandidateConfigService candidateConfigService,
            @Value("${agent-doc.experiment-report.proof-key:}") String key) {
        this.candidateConfigService = candidateConfigService;
        this.key = key;
    }

    @GetMapping("/api/agent/internal/experiment-report/candidate-configs/{id}")
    public Result<AgentCandidateConfigVO> identity(@PathVariable Long id, @RequestParam Long spaceId,
            @RequestParam String candidateSnapshotHash,
            @RequestHeader(value = ReportProofSignature.HEADER, required = false) String proof) {
        if (!ReportProofSignature.verify(key, id, spaceId, candidateSnapshotHash, proof, Instant.now())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "报告证明无效或已过期");
        }
        return Result.ok(candidateConfigService.requireRestorableIdentity(id, spaceId, candidateSnapshotHash));
    }
}
