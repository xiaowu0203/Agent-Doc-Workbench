package com.agentdoc.agent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Prompt处理服务
 * <p>
 * 加载平台级基础系统提示词资源；拼接平台公共提示词 + Agent自定义提示词；
 * 对完整提示词+指令做SHA‑256哈希，用于生成promptHash，记录在AgentExecution执行快照中，用于版本追踪。
 * </p>
 */
@Service
public class PromptService {
    /**
     * 平台公共基础系统提示词，从配置指定的资源文件一次性加载
     */
    private final String platformPrompt;

    /**
     * 构造器注入，读取外部资源文件加载平台级公共prompt
     *
     * @param promptResource 资源路径配置：{@code agent-doc.agent.platform-prompt-resource}，如classpath下txt文件
     * @throws IllegalStateException 文件读取IO异常时抛出，服务启动失败
     */
    public PromptService(@Value("${agent-doc.agent.platform-prompt-resource}") Resource promptResource) {
        try {
            this.platformPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("平台系统提示词读取失败", exception);
        }
    }

    /**
     * 组装完整systemPrompt：平台公共提示词 拼接 Agent自定义提示词
     * <p>中间用换行分隔；Agent自定义prompt为null则使用空字符串。</p>
     *
     * @param agentPrompt Agent配置的自定义系统提示词，可为null
     * @return 拼接完成的完整系统提示文本
     */
    public String systemPrompt(String agentPrompt) {
        String configuredPrompt = agentPrompt == null ? "" : agentPrompt;
        return platformPrompt + System.lineSeparator() + System.lineSeparator() + configuredPrompt;
    }

    /**
     * 对【完整systemPrompt + 用户指令】计算SHA‑256哈希，输出十六进制字符串
     * <p>
     * 用于生成promptHash存入AgentExecutionEntity执行记录；
     * 可用来快速判断两次任务使用的提示词文本是否发生变化，用于审计与问题复现。
     * </p>
     *
     * @param systemPrompt 拼接完成的完整系统提示词
     * @param instruction  用户任务指令
     * @return SHA‑256十六进制哈希字符串
     * @throws IllegalStateException JDK不支持SHA‑256算法时抛出（几乎不会发生）
     */
    public String hash(String systemPrompt, String instruction) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (systemPrompt + System.lineSeparator() + instruction).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
