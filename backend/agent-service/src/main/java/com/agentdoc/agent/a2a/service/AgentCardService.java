package com.agentdoc.agent.a2a.service;

import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.HTTPAuthSecurityScheme;
import org.a2aproject.sdk.spec.SecurityRequirement;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Agent‑Card 元信息服务
 * <p>
 * 生成A2A协议标准的Agent Card描述文档，对外暴露本Agent的能力集、支持模式、安全认证方案、访问接口地址；
 * 由 {@code /.well‑known/agent‑card.json} 端点对外提供，供A2A客户端发现与握手。
 * </p>
 */
@Service
public class AgentCardService {

    /** Agent服务版本号 */
    private static final String AGENT_VERSION = "0.1.0";
    /** Skill唯一标识：文档协作能力 */
    private static final String SKILL_ID = "document-collaboration";
    /** 文本输入输出模式 */
    private static final String TEXT_MODE = "text/plain";
    /** JSON结构化输入输出模式 */
    private static final String JSON_MODE = "application/json";
    /** 安全方案标识：任务能力令牌 */
    private static final String TASK_CAPABILITY_SCHEME = "taskCapability";
    /** HTTP Auth Bearer 认证模式 */
    private static final String BEARER_SCHEME = "bearer";
    /** Token格式为JWT */
    private static final String JWT_FORMAT = "JWT";

    /** 服务对外公网访问根地址，配置项：agent‑doc.agent.public‑url */
    private final String publicUrl;

    /**
     * @param publicUrl 对外可访问的服务公网URL，从配置文件注入
     */
    public AgentCardService(@Value("${agent-doc.agent.public-url}") String publicUrl) {
        this.publicUrl = publicUrl;
    }

    /**
     * 构建A2A AgentCard元描述对象
     * <p>包含Agent名称版本、Skill能力、输入输出模式、支持特性、安全认证、协议接入地址。</p>
     *
     * @return AgentCard A2A标准Agent元信息卡片
     */
    public AgentCard get() {
        List<String> modes = List.of(TEXT_MODE, JSON_MODE);
        // 定义文档协作Skill能力描述
        AgentSkill skill = AgentSkill.builder()
                .id(SKILL_ID)
                .name("Document Collaboration")
                .description("Reads Workbench documents and proposes scoped changes through MCP tools")
                .tags(List.of("document", "editing", "mcp"))
                .examples(List.of("Review this document and improve its structure"))
                .inputModes(modes)
                .outputModes(modes)
                .build();
        return AgentCard.builder()
                .name("Agent-Doc Workbench Agent Server")
                .description("Hosted document collaboration agents for Agent-Doc-Workbench")
                .version(AGENT_VERSION)
                // 声明协议能力：支持SSE流式、支持推送回调通知
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .pushNotifications(true)
                        .build())
                .defaultInputModes(modes)
                .defaultOutputModes(modes)
                .skills(List.of(skill))
                // 安全方案定义：使用 taskCapability 的 Bearer‑JWT 任务作用域令牌
                .securitySchemes(Map.of(TASK_CAPABILITY_SCHEME, HTTPAuthSecurityScheme.builder()
                        .scheme(BEARER_SCHEME)
                        .bearerFormat(JWT_FORMAT)
                        .description("Workbench task-scoped capability token")
                        .build()))
                // 声明本Agent接口必须使用上面定义的安全方案
                .securityRequirements(List.of(SecurityRequirement.builder()
                        .scheme(TASK_CAPABILITY_SCHEME, List.of())
                        .build()))
                // 声明支持的传输协议与接入端点地址
                .supportedInterfaces(List.of(new AgentInterface(
                        TransportProtocol.HTTP_JSON.asString(), publicUrl + "/a2a")))
                .build();
    }
}
