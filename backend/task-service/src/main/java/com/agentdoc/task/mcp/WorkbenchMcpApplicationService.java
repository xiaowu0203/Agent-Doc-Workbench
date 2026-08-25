package com.agentdoc.task.mcp;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.vo.DocumentExecutionContextVO;
import com.agentdoc.common.feign.vo.DocumentFragmentVO;
import com.agentdoc.task.constant.TaskConstant;
import com.agentdoc.task.enums.ChangeRequestStatus;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.service.ChangeRequestService;
import com.agentdoc.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Workbench MCP 应用业务服务
 * <p>
 * 面向Agent的MCP工具入口服务，所有MCP工具调用统一在此层接收。
 * 每一个工具方法首先通过{@link McpTaskScopeService}校验任务能力令牌与对应Action权限，
 * 拿到MCP安全作用域后，再调用文档Feign接口、变更申请服务完成业务逻辑。
 * 负责读取文档片段、获取任务执行上下文、Agent提交文档变更提案三类能力。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class WorkbenchMcpApplicationService {

    private final McpTaskScopeService scopeService;
    private final TaskService taskService;
    private final ChangeRequestService changeRequestService;
    private final DocumentFeign documentFeign;

    /**
     * 获取Agent执行任务的文档上下文
     * <p>权限校验：校验令牌具备 {@value JwtConstant#ACTION_READ_FRAGMENT} 读片段权限。
     * 通过Feign远程调用文档服务，返回文档执行上下文VO。</p>
     *
     * @return 文档执行上下文对象
     * @throws BusinessException 令牌校验失败、权限不足、文档服务调用异常时抛出
     */
    public DocumentExecutionContextVO getTaskContext() {
        //  获取当前任务的范围，需要验证ACTION_READ_FRAGMENT权限
        McpTaskScope scope = scopeService.require(JwtConstant.ACTION_READ_FRAGMENT);
        // 远程调用查询【Agent任务执行上下文】，并包装返回结果
        return requireData(documentFeign.getExecutionContext(scope.documentId()));
    }

    /**
     * 读取文档指定偏移、指定长度的片段内容
     * <p>入参校验偏移量、读取长度，防止超限读取；
     * 权限校验：校验令牌具备 {@value JwtConstant#ACTION_READ_FRAGMENT} 读片段权限；
     * 读取范围受 {@link TaskConstant#MAX_MCP_FRAGMENT_LENGTH} 最大片段长度限制。</p>
     *
     * @param start  读取起始偏移
     * @param length 读取字节长度
     * @return 文档片段VO，包含片段文本与版本信息
     * @throws BusinessException 参数非法、令牌校验失败、权限不足、文档服务调用异常时抛出
     */
    public DocumentFragmentVO readDocumentFragment(long start, int length) {
        if (start < 0 || length <= 0 || length > TaskConstant.MAX_MCP_FRAGMENT_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文档片段范围无效");
        }
        //  获取当前任务的范围，需要验证ACTION_READ_FRAGMENT权限
        McpTaskScope scope = scopeService.require(JwtConstant.ACTION_READ_FRAGMENT);
        // 远程调用获取【文档片段】并封装结果
        return requireData(documentFeign.readFragment(scope.documentId(), start, length));
    }

    /**
     * Agent提交文档变更提案，生成变更申请单
     * <p>权限校验：校验令牌具备 {@value JwtConstant#ACTION_CREATE_CHANGE_REQUEST} 创建变更权限；
     * 校验提案非空、基准版本与变更集合有效；由Agent侧发起提交变更请求，生成ChangeRequestEntity记录。</p>
     *
     * @param proposal Agent侧提交的变更提案，包含基准版本与变更集合
     * @return MCP变更提案结果，返回变更申请ID与状态名称
     * @throws BusinessException 参数非法、令牌校验失败、权限不足、任务不存在时抛出
     */
    public McpChangeProposalResult proposeChanges(McpChangeProposal proposal) {
        if (proposal == null || proposal.baseVersion() == null
                || proposal.changes() == null || proposal.changes().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "变更提案不能为空");
        }
        //  获取当前任务的范围，需要验证ACTION_CREATE_CHANGE_REQUEST权限
        McpTaskScope scope = scopeService.require(JwtConstant.ACTION_CREATE_CHANGE_REQUEST);
        // 获取任务信息
        TaskEntity task = taskService.require(scope.taskId());
        // 提交变更提案，生成变更申请单
        ChangeRequestEntity request = changeRequestService.submitFromAgent(
                task, proposal.changes(), proposal.baseVersion());
        return new McpChangeProposalResult(
                request.getId(), ChangeRequestStatus.fromCode(request.getStatus()).name());
    }

    /**
     * Feign返回结果通用解析工具方法
     * <p>校验远程调用返回结果，非成功状态或者data为空时包装抛出业务异常。</p>
     *
     * @param result Feign远程调用返回Result封装对象
     * @param <T>    返回数据泛型
     * @return 提取后的业务data数据
     * @throws BusinessException 远程调用失败、返回数据为空时抛出
     */
    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "文档服务调用失败" : result.message());
        }
        return result.data();
    }
}
