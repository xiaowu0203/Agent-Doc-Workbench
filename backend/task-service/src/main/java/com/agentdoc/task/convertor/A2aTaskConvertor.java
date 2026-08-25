package com.agentdoc.task.convertor;

import com.agentdoc.common.constant.A2aMetadataConstant;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.a2a.A2aTokenUsage;
import com.agentdoc.task.pojo.entity.TaskEntity;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * A2A任务转换器
 * <p>
 * 负责远端A2A任务对象与本地工作台{@link TaskEntity}实体之间的数据映射转换，
 * 包含状态枚举映射、结果摘要提取、元数据解析、Token用量统计、终态时间回填等逻辑。
 * 纯静态工具类，不允许实例化。
 * </p>
 */
public final class A2aTaskConvertor {

    private A2aTaskConvertor() {
    }

    /**
     * 将远端A2A任务数据应用更新到本地任务实体
     * <ul>
     * <li>回填A2A远端任务ID、上下文ID、最后心跳时间</li>
     * <li>转换任务状态并更新本地状态码</li>
     * <li>解析执行摘要Artifact，回填结果摘要与元数据</li>
     * <li>失败状态回填错误信息</li>
     * <li>任务进入终态时回填结束时间</li>
     * </ul>
     *
     * @param entity      本地工作台任务实体（会被直接修改）
     * @param remoteTask  远端A2A Agent‑Server返回任务对象
     */
    public static void apply(TaskEntity entity, Task remoteTask) {
        // 回填远端A2A任务标识与心跳时间
        entity.setA2aTaskId(remoteTask.id());
        entity.setA2aContextId(remoteTask.contextId());
        entity.setLastHeartbeatAt(LocalDateTime.now());
        // A2A远端状态映射为本地任务状态
        TaskStatus status = mapStatus(remoteTask.status().state());
        entity.setStatus(status.getCode());
        // 提取执行摘要Artifact
        Artifact summaryArtifact = summaryArtifact(remoteTask.artifacts());
        if (summaryArtifact != null) {
            // 提取摘要文本存入结果摘要字段
            entity.setResultSummary(text(summaryArtifact.parts()));
            // 解析摘要内元数据，更新token用量、执行ID、prompt哈希
            applyMetadata(entity, summaryArtifact.metadata());
        }
        // 任务失败，回填错误消息
        if (status == TaskStatus.FAILED) {
            entity.setErrorMessage(messageText(remoteTask.status().message()));
        }
        // 任务为终态，记录任务结束时间
        if (isFinal(status)) {
            entity.setEndTime(LocalDateTime.now());
        }
    }

    /**
     * A2A远端任务状态枚举 → 本地工作台任务状态枚举映射
     *
     * @param state A2A协议定义任务状态
     * @return 本地{@link TaskStatus}状态
     * @throws IllegalArgumentException 遇到未指定的未知状态抛出异常
     */
    public static TaskStatus mapStatus(TaskState state) {
        return switch (state) {
            case TASK_STATE_SUBMITTED -> TaskStatus.DISPATCHED;
            case TASK_STATE_WORKING -> TaskStatus.RUNNING;
            case TASK_STATE_INPUT_REQUIRED -> TaskStatus.WAITING_INPUT;
            case TASK_STATE_AUTH_REQUIRED -> TaskStatus.WAITING_AUTH;
            case TASK_STATE_COMPLETED -> TaskStatus.COMPLETED;
            case TASK_STATE_CANCELED -> TaskStatus.TERMINATED;
            case TASK_STATE_FAILED, TASK_STATE_REJECTED -> TaskStatus.FAILED;
            case TASK_STATE_UNSPECIFIED -> throw new IllegalArgumentException("A2A Task 状态未指定");
        };
    }

    /**
     * 从远端A2A任务解析Token消耗用量
     * <p>从执行摘要Artifact的metadata读取输入Token、缓存输入Token、输出Token</p>
     *
     * @param remoteTask 远端A2A任务对象
     * @return A2A Token用量对象，无元数据时各用量保持null
     */
    public static A2aTokenUsage tokenUsage(Task remoteTask) {
        // 从Artifact列表筛选出【执行摘要】Artifact
        Artifact artifact = summaryArtifact(remoteTask.artifacts());
        // 解析元数据并返回Token用量
        Map<String, Object> metadata = artifact == null ? null : artifact.metadata();
        if (metadata == null) {
            return new A2aTokenUsage(null, null, null, false, false, false);
        }
        return new A2aTokenUsage(
                nullableLong(metadata.get(A2aMetadataConstant.INPUT_TOKENS)),
                nullableLong(metadata.get(A2aMetadataConstant.CACHED_INPUT_TOKENS)),
                nullableLong(metadata.get(A2aMetadataConstant.OUTPUT_TOKENS)),
                booleanValue(metadata.get(A2aMetadataConstant.INPUT_TOKENS_ESTIMATED)),
                booleanValue(metadata.get(A2aMetadataConstant.CACHED_INPUT_TOKENS_ESTIMATED)),
                booleanValue(metadata.get(A2aMetadataConstant.OUTPUT_TOKENS_ESTIMATED)));
    }

    /**
     * 解析元数据并更新本地任务实体相关字段
     * <p>总Token消耗 = 输入Token + 输出Token；同时回填agent执行ID、prompt哈希</p>
     *
     * @param entity   本地任务实体
     * @param metadata A2A执行摘要返回元数据map
     */
    private static void applyMetadata(TaskEntity entity, Map<String, Object> metadata) {
        if (metadata == null) {
            return;
        }
        // 获取输入token
        Long inputTokens = nullableLong(metadata.get(A2aMetadataConstant.INPUT_TOKENS));
        // 获取输出token
        Long outputTokens = nullableLong(metadata.get(A2aMetadataConstant.OUTPUT_TOKENS));
        // 设置总token
        entity.setTokensUsed(sum(inputTokens, outputTokens));
        entity.setTokensEstimated(booleanValue(metadata.get(A2aMetadataConstant.INPUT_TOKENS_ESTIMATED))
                || booleanValue(metadata.get(A2aMetadataConstant.OUTPUT_TOKENS_ESTIMATED)));
        // 回填agent执行ID
        entity.setAgentExecutionId(nullableLong(metadata.get(A2aMetadataConstant.AGENT_EXECUTION_ID)));
        // 获取prompt哈希
        Object promptHash = metadata.get(A2aMetadataConstant.PROMPT_HASH);
        // 回填prompt哈希
        entity.setPromptHash(promptHash == null ? null : String.valueOf(promptHash));
    }

    /**
     * 从Artifact列表筛选出【执行摘要】Artifact
     *
     * @param artifacts A2A返回产物列表
     * @return 匹配名称的摘要Artifact，找不到返回null
     */
    private static Artifact summaryArtifact(List<Artifact> artifacts) {
        if (artifacts == null) {
            return null;
        }
        return artifacts.stream()
                .filter(artifact -> A2aMetadataConstant.EXECUTION_SUMMARY_ARTIFACT.equals(artifact.name()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 提取Message对象中的文本内容
     *
     * @param message A2A消息对象，可为null
     * @return 提取出的纯文本，无文本返回null
     */
    private static String messageText(Message message) {
        return message == null ? null : text(message.parts());
    }

    /**
     * 从Part片段列表提取第一个TextPart文本内容
     *
     * @param parts 消息片段集合
     * @return 首个文本片段内容，无文本返回null
     */
    private static String text(List<Part<?>> parts) {
        if (parts == null) {
            return null;
        }
        return parts.stream()
                .filter(TextPart.class::isInstance)
                .map(TextPart.class::cast)
                .map(TextPart::text)
                .findFirst()
                .orElse(null);
    }

    /**
     * 对象安全转Long包装类型，null保持null
     *
     * @param value 待转换对象
     * @return Long实例；输入null返回null
     */
    private static Long nullableLong(Object value) {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private static boolean booleanValue(Object value) {
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static Long sum(Long left, Long right) {
        return left == null || right == null ? null : left + right;
    }

    /**
     * 判断本地任务状态是否属于终态：完成 / 终止 / 失败
     *
     * @param status 本地任务状态
     * @return true=终态
     */
    private static boolean isFinal(TaskStatus status) {
        return status == TaskStatus.COMPLETED || status == TaskStatus.TERMINATED || status == TaskStatus.FAILED;
    }
}
