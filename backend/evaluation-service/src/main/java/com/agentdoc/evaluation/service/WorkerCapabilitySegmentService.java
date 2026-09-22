package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.evaluation.enums.EvaluationWorkerCapabilityStatus;
import com.agentdoc.evaluation.mapper.EvaluationWorkerCapabilitySegmentMapper;
import com.agentdoc.evaluation.pojo.entity.EvaluationWorkerCapabilitySegmentEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * WorkerCapability Segment 的不可变追加与受控解密入口。
 * 按Run+batchNo维度维护能力分片记录；新增分片幂等，并发冲突自动处理；
 * 提供读取激活未过期的Capability明文、获取下一个batchNo的能力。
 */
@Service
@RequiredArgsConstructor
public class WorkerCapabilitySegmentService {

    private final EvaluationWorkerCapabilitySegmentMapper mapper;
    private final WorkerCapabilityCryptoService cryptoService;

    /**
     * 追加一个Capability分片，runId+batchNo为唯一键，幂等写入。
     * 并发插入冲突捕获DuplicateKeyException，查询已有记录并校验内容一致，防止同batch被不同参数覆盖。
     * @param runId 评估任务ID
     * @param spaceId 空间ID
     * @param batchNo 批次号
     * @param taskSetHash 任务集合哈希(64位)
     * @param capability 原始能力明文
     * @param expiresAt 过期时间
     * @return 数据库分片实体
     */
    @Transactional
    public EvaluationWorkerCapabilitySegmentEntity append(Long runId, Long spaceId, Integer batchNo,
                                                          String taskSetHash, String capability,
                                                          Instant expiresAt) {
        validate(runId, spaceId, batchNo, taskSetHash, capability, expiresAt);
        EvaluationWorkerCapabilitySegmentEntity existing = find(runId, batchNo);
        if (existing != null) {
            return requireSame(existing, spaceId, taskSetHash, expiresAt);
        }

        // 加密能力凭证
        WorkerCapabilityCryptoService.EncryptedCapability encrypted = cryptoService.encrypt(capability);
        EvaluationWorkerCapabilitySegmentEntity entity = new EvaluationWorkerCapabilitySegmentEntity();
        entity.setId(IdWorker.getId());
        entity.setRunId(runId);
        entity.setSpaceId(spaceId);
        entity.setBatchNo(batchNo);
        entity.setTaskSetHash(taskSetHash);
        entity.setEncryptedCapability(encrypted.ciphertext());
        entity.setKeyVersion(encrypted.keyVersion());
        entity.setStatus(EvaluationWorkerCapabilityStatus.ACTIVE.name());
        entity.setExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault()));

        try {
            mapper.insert(entity);
            return entity;
        } catch (DuplicateKeyException exception) {
            // 并发场景：别的线程已经插入，重新查询并校验数据一致性
            EvaluationWorkerCapabilitySegmentEntity concurrent = find(runId, batchNo);
            if (concurrent == null) {
                throw exception;
            }
            return requireSame(concurrent, spaceId, taskSetHash, expiresAt);
        }
    }

    /**
     * 获取激活、未过期的Capability明文。
     * 校验segment归属、状态、过期时间，再解密返回明文。
     */
    public String requireActiveCapability(Long segmentId, Long runId, Long spaceId) {
        EvaluationWorkerCapabilitySegmentEntity entity = mapper.selectById(segmentId);
        if (entity == null || !runId.equals(entity.getRunId()) || !spaceId.equals(entity.getSpaceId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "WorkerCapability Segment 不存在");
        }
        if (!EvaluationWorkerCapabilityStatus.ACTIVE.name().equals(entity.getStatus())
                || entity.getExpiresAt() == null || !entity.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.CONFLICT, "WorkerCapability 已过期或撤销");
        }
        return cryptoService.decrypt(entity.getKeyVersion(), entity.getEncryptedCapability());
    }

    /**
     * 一次加载多个能力分片的明文 Capability，逐个沿用单分片的归属、状态与过期校验。
     */
    public Map<Long, String> requireActiveCapabilities(Collection<Long> segmentIds, Long runId, Long spaceId) {
        List<Long> ids = segmentIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<EvaluationWorkerCapabilitySegmentEntity> entities = mapper.selectList(
                new LambdaQueryWrapper<EvaluationWorkerCapabilitySegmentEntity>()
                        .in(EvaluationWorkerCapabilitySegmentEntity::getId, ids));
        Map<Long, EvaluationWorkerCapabilitySegmentEntity> byId = entities.stream()
                .collect(Collectors.toMap(EvaluationWorkerCapabilitySegmentEntity::getId, value -> value));
        Map<Long, String> capabilities = new LinkedHashMap<>();
        for (Long segmentId : ids) {
            EvaluationWorkerCapabilitySegmentEntity entity = byId.get(segmentId);
            if (entity == null || !runId.equals(entity.getRunId()) || !spaceId.equals(entity.getSpaceId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "WorkerCapability Segment 不存在");
            }
            if (!EvaluationWorkerCapabilityStatus.ACTIVE.name().equals(entity.getStatus())
                    || entity.getExpiresAt() == null || !entity.getExpiresAt().isAfter(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.CONFLICT, "WorkerCapability 已过期或撤销");
            }
            capabilities.put(segmentId, cryptoService.decrypt(entity.getKeyVersion(), entity.getEncryptedCapability()));
        }
        return capabilities;
    }

    /**
     * 获取当前Run下可用的下一个batchNo：取已有最大batchNo +1；无记录则返回1
     */
    public int nextBatchNo(Long runId) {
        return mapper.selectList(new LambdaQueryWrapper<EvaluationWorkerCapabilitySegmentEntity>()
                        .eq(EvaluationWorkerCapabilitySegmentEntity::getRunId, runId)).stream()
                .map(EvaluationWorkerCapabilitySegmentEntity::getBatchNo)
                .max(Comparator.naturalOrder()).orElse(0) + 1;
    }

    /** 根据runId + batchNo查询分片记录 */
    private EvaluationWorkerCapabilitySegmentEntity find(Long runId, Integer batchNo) {
        return mapper.selectOne(new LambdaQueryWrapper<EvaluationWorkerCapabilitySegmentEntity>()
                .eq(EvaluationWorkerCapabilitySegmentEntity::getRunId, runId)
                .eq(EvaluationWorkerCapabilitySegmentEntity::getBatchNo, batchNo));
    }

    /**
     * 幂等一致性校验：同一run+batchNo必须拥有完全一致的spaceId、taskSetHash、过期时间。
     * 如果参数不一致，说明有人试图复用同一个batchNo写入不同能力配置，直接拒绝。
     */
    private EvaluationWorkerCapabilitySegmentEntity requireSame(EvaluationWorkerCapabilitySegmentEntity existing,
                                                                Long spaceId, String taskSetHash,
                                                                Instant expiresAt) {
        LocalDateTime expectedExpiry = LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault());
        if (!spaceId.equals(existing.getSpaceId()) || !taskSetHash.equals(existing.getTaskSetHash())
                || !expectedExpiry.equals(existing.getExpiresAt())) {
            throw new BusinessException(ErrorCode.CONFLICT, "WorkerCapability 批次号已被不同请求占用");
        }
        return existing;
    }

    /** 参数合法性校验 */
    private void validate(Long runId, Long spaceId, Integer batchNo, String taskSetHash,
                          String capability, Instant expiresAt) {
        if (runId == null || spaceId == null || batchNo == null || batchNo < 1
                || taskSetHash == null || taskSetHash.length() != 64
                || capability == null || capability.isBlank() || expiresAt == null
                || !expiresAt.isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "WorkerCapability Segment 参数无效");
        }
    }
}
