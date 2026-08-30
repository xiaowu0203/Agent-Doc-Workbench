package com.agentdoc.agent.execution.audit;

import com.agentdoc.agent.enums.ModelCallAuditStatus;
import com.agentdoc.agent.execution.model.ModelAdapterContext;
import com.agentdoc.agent.mapper.AgentExecutionModelCallMapper;
import com.agentdoc.agent.pojo.entity.AgentExecutionModelCallEntity;
import com.agentdoc.common.utils.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent【模型调用】审计服务
 * <p>
 * 负责模型调用全生命周期审计记录：启动、成功、失败、僵死任务补偿。
 * 每条模型对外请求都会生成一条审计记录；保存消息快照、响应快照、SHA256摘要、数据大小，用于溯源对账。
 * <p>
 * 事务说明：全部使用 {@link Propagation#REQUIRES_NEW} 新建独立事务。
 * 即使外层主业务事务回滚，审计记录依旧可以落库，保证审计日志不随业务回滚丢失。
 */
@Service
@RequiredArgsConstructor
public class AgentExecutionModelCallAuditService {

    private final AgentExecutionModelCallMapper mapper;

    /**
     * 创建并启动一条模型调用审计记录
     * <p>
     * 开启独立新事务；记录调用上下文、入参消息快照、消息体SHA256哈希、消息字节大小；初始状态为 STARTED。
     * @param context     模型适配器上下文，携带执行ID、模型元信息、参数
     * @param sequence    当前模型调用序号，同一次Agent执行内的调用递增序号
     * @param messages    请求模型的完整消息列表
     * @param streaming   是否流式调用
     * @return 已持久化的模型调用审计实体
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentExecutionModelCallEntity start(ModelAdapterContext context, int sequence,
                                               List<Message> messages, boolean streaming) {
        // 将消息列表序列化为快照字节数组
        byte[] payload = JsonUtils.toJson(messageSnapshots(messages)).getBytes(StandardCharsets.UTF_8);

        // 构建实体
        AgentExecutionModelCallEntity entity = new AgentExecutionModelCallEntity();
        entity.setExecutionId(context.executionId());
        entity.setSequenceNo(sequence);
        entity.setModelId(context.model().getId());
        entity.setModelConfigVersion(context.model().getConfigVersion());
        entity.setModelKey(context.model().getModelKey());
        entity.setMaxOutputTokens(context.maxOutputTokens());
        entity.setTemperature(context.temperature());
        entity.setStreaming(streaming);
        // 入参消息SHA256摘要，用于数据完整性校验、对账
        entity.setMessagesSha256(sha256(payload));
        // 原始消息快照字节大小
        entity.setMessagesSize((long) payload.length);
        entity.setStatus(ModelCallAuditStatus.STARTED.name());
        entity.setStartedAt(LocalDateTime.now());

        // 入库
        requireInserted(mapper.insert(entity));
        return entity;
    }

    /**
     * 标记模型调用审计为成功结束
     * <p>
     * 独立新事务；记录响应快照、响应SHA256、响应大小，更新状态为SUCCEEDED，填充完成时间。
     *
     * @param entity 审计实体
     * @param response 模型返回原始响应对象
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(AgentExecutionModelCallEntity entity, ChatResponse response) {
        // 将响应对象序列化为快照字节数组
        byte[] payload = JsonUtils.toJson(responseSnapshot(response)).getBytes(StandardCharsets.UTF_8);
        // 入参消息SHA256摘要，用于数据完整性校验、对账
        entity.setResponseSha256(sha256(payload));
        // 原始消息快照字节大小
        entity.setResponseSize((long) payload.length);
        // 设置状态为成功、成功时间
        entity.setStatus(ModelCallAuditStatus.SUCCEEDED.name());
        entity.setFinishedAt(LocalDateTime.now());
        requireUpdated(mapper.updateById(entity));
    }

    /**
     * 标记模型调用审计为失败结束
     * <p>
     * 独立新事务；更新状态为FAILED，记录错误类型，填充完成时间。
     *
     * @param entity 审计实体
     * @param errorType 错误类型编码，用于归类统计失败原因
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(AgentExecutionModelCallEntity entity, String errorType) {
        // 更新状态为失败，失败类型，失败的时间
        entity.setStatus(ModelCallAuditStatus.FAILED.name());
        entity.setErrorType(errorType);
        entity.setFinishedAt(LocalDateTime.now());
        requireUpdated(mapper.updateById(entity));
    }

    /**
     * 批量补偿僵死未结束的模型调用审计记录
     * <p>
     * 由定时任务 {@link AgentExecutionAuditReconcileJob} 调用。
     * 将状态为STARTED、开始时间早于cutoff阈值的僵死记录，强制置为失败，错误标记：AUDIT_FINALIZATION_MISSING。
     *
     * @param cutoff 时间阈值，早于此时间且仍未结束视为僵死任务
     * @return 本次更新处理的记录行数
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int failStaleStarted(LocalDateTime cutoff) {
        // 将【僵死记录】置为【失败】
        LambdaUpdateWrapper<AgentExecutionModelCallEntity> wrapper = new LambdaUpdateWrapper<AgentExecutionModelCallEntity>()
                .eq(AgentExecutionModelCallEntity::getStatus, ModelCallAuditStatus.STARTED.name())
                .lt(AgentExecutionModelCallEntity::getStartedAt, cutoff)
                .set(AgentExecutionModelCallEntity::getStatus, ModelCallAuditStatus.FAILED.name())
                .set(AgentExecutionModelCallEntity::getErrorType, "AUDIT_FINALIZATION_MISSING")
                .set(AgentExecutionModelCallEntity::getFinishedAt, LocalDateTime.now());
        return mapper.update(null, wrapper);
    }

    /**
     * 将Message列表转为可序列化的快照Map集合，用于持久化审计入参
     *
     * @param messages 原始消息列表
     * @return 序列化快照集合
     */
    private List<Map<String, Object>> messageSnapshots(List<Message> messages) {
        return messages.stream().map(this::messageSnapshot).toList();
    }

    /**
     * 单条消息对象转快照Map
     * <p>
     * 根据消息类型区分：普通文本、Assistant消息(携带toolCalls)、ToolResponse消息(携带toolResponses)。
     * 使用record快照对象做结构化存储，避免直接序列化领域对象带来额外字段。
     *
     * @param message 原始领域消息对象
     * @return 快照Map，用于JSON持久化
     */
    private Map<String, Object> messageSnapshot(Message message) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("type", message.getMessageType().name());
        snapshot.put("text", message.getText());

        // Assistant消息(携带toolCalls)
        if (message instanceof AssistantMessage assistant) {
            snapshot.put("toolCalls", assistant.getToolCalls().stream()
                    .map(call -> new ToolCallSnapshot(call.id(), call.type(), call.name(), call.arguments()))
                    .toList());
        }
        // ToolResponse消息(携带toolResponses)
        else if (message instanceof ToolResponseMessage toolResponse) {
            snapshot.put("toolResponses", toolResponse.getResponses().stream()
                    .map(response -> new ToolResponseSnapshot(response.id(), response.name(), response.responseData()))
                    .toList());
        }
        return snapshot;
    }

    /**
     * 模型响应对象转为快照Map
     *
     * @param response 模型ChatResponse响应
     * @return 响应快照Map；空响应返回空Map
     */
    private Map<String, Object> responseSnapshot(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return Map.of();
        }
        return messageSnapshot(response.getResult().getOutput());
    }

    /**
     * 计算字节数组的SHA‑256十六进制摘要
     *
     * @param value 待计算字节数组
     * @return 小写十六进制SHA‑256字符串
     * @throws IllegalStateException 当前JDK不支持SHA‑256算法时抛出
     */
    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    /**
     * 校验单条更新生效行数，必须更新1行，否则抛出异常。
     * 用于防止记录不存在、已被并发修改导致审计状态丢失。
     *
     * @param rows mybatis‑plus update返回影响行数
     */
    private void requireUpdated(int rows) {
        if (rows != 1) {
            throw new IllegalStateException("模型调用审计状态更新失败");
        }
    }

    /**
     * 校验插入生效行数，必须插入1行，否则抛出异常。
     *
     * @param rows mybatis‑plus insert返回影响行数
     */
    private void requireInserted(int rows) {
        if (rows != 1) {
            throw new IllegalStateException("模型调用审计记录创建失败");
        }
    }

    /**
     * ToolCall 快照记录，用于JSON持久化审计，裁剪领域对象多余字段
     * @param id 工具调用ID
     * @param type 类型
     * @param name 工具名称
     * @param arguments 调用参数
     */
    private record ToolCallSnapshot(String id, String type, String name, String arguments) {
    }

    /**
     * ToolResponse 快照记录，用于JSON持久化审计
     * @param id 工具调用ID
     * @param name 工具名称
     * @param responseData 工具返回结果数据
     */
    private record ToolResponseSnapshot(String id, String name, String responseData) {
    }
}
