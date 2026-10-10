package com.agentdoc.evaluation.service;

import com.agentdoc.evaluation.mapper.OnlinePreflightProofMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlinePreflightProofEntity;
import com.agentdoc.common.utils.AuthUtils;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 网络预检完成之后保存权威证明，不在预检 RPC 期间持数据库事务。 */
@Service
@RequiredArgsConstructor
public class OnlinePreflightProofService {
    private final OnlinePreflightProofMapper proofs;
    public void save(OnlineExperimentEntity experiment, String dependencyHash, String proofHash, Instant expires) {
        var proof = new OnlinePreflightProofEntity(); proof.setId(IdWorker.getId()); proof.setExperimentId(experiment.getId());
        proof.setActorId(AuthUtils.getUserIdOrException()); proof.setManifestHash(experiment.getManifestHash());
        proof.setDependencyHash(dependencyHash); proof.setStateVersion(experiment.getStateVersion()); proof.setProofHash(proofHash);
        proof.setExpiresAt(LocalDateTime.ofInstant(expires, ZoneOffset.UTC)); proofs.insert(proof);
    }
}
