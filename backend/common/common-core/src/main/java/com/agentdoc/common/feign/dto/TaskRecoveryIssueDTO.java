package com.agentdoc.common.feign.dto;

/**
 * 仅由机器认证的 task-service 发送；不能从浏览器透传。
 * @param sourceCapability 原签名历史证明，禁止记录
 * @param identity 数据库重新读取的冻结身份
 * @param a2aTaskId 本地已关联的远端任务 ID
 * @param recoveryId 本次恢复关联 UUID
 * @param cancelRequested 本地已经记录的取消意图
 * @param remoteTerminalStatus 草稿收尾时的可信远端终态；查询签发为空
 */
public record TaskRecoveryIssueDTO(String sourceCapability, TaskRecoveryIdentityDTO identity,
                                   String a2aTaskId, String recoveryId, boolean cancelRequested,
                                   String remoteTerminalStatus) {
    @Override
    public String toString() {
        return "TaskRecoveryIssueDTO[凭证已隐藏]";
    }
}
