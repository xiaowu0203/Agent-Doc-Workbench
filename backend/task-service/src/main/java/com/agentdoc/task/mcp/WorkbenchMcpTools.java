package com.agentdoc.task.mcp;

import com.agentdoc.common.feign.vo.DocumentExecutionContextVO;
import com.agentdoc.common.feign.vo.DocumentFragmentVO;
import com.agentdoc.task.constant.TaskConstant;
import lombok.RequiredArgsConstructor;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * Workbench MCP工具定义层
 * <p>
 * MCP工具对外暴露门面，通过注解{@code @McpTool}向MCP协议注册工具名称、描述与入参说明。
 * 本身不实现业务逻辑，仅做工具声明，将请求转发至{@link WorkbenchMcpApplicationService}完成鉴权与业务处理。
 * Agent通过MCP协议调用这里注册的工具，所有权限校验、参数校验均下沉到ApplicationService层。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class WorkbenchMcpTools {

    private final WorkbenchMcpApplicationService applicationService;

    @McpTool(name = "workbench_get_task_context",
            description = "获取当前能力令牌所绑定的任务和文档执行上下文")
    public DocumentExecutionContextVO getTaskContext() {
        return applicationService.getTaskContext();
    }

    @McpTool(name = "workbench_read_document_fragment",
            description = "读取当前任务所绑定文档的指定片段")
    public DocumentFragmentVO readDocumentFragment(
            @McpToolParam(description = "从零开始的字符偏移量", required = true) long start,
            @McpToolParam(description = "读取字符数，最大 " + TaskConstant.MAX_MCP_FRAGMENT_LENGTH,
                    required = true) int length) {
        return applicationService.readDocumentFragment(start, length);
    }

    @McpTool(name = "workbench_propose_changes",
            description = "为当前任务绑定的文档提交待人工审批的结构化变更提案")
    public McpChangeProposalResult proposeChanges(
            @McpToolParam(description = "基线版本和结构化变更项", required = true)
            McpChangeProposal proposal) {
        return applicationService.proposeChanges(proposal);
    }
}
