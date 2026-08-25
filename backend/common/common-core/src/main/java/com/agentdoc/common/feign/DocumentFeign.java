package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.MergeRequestDTO;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.feign.vo.DocumentExecutionContextVO;
import com.agentdoc.common.feign.vo.DocumentFragmentVO;
import com.agentdoc.common.feign.vo.MergeResultVO;
import com.agentdoc.common.feign.vo.SpaceBudgetVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文档服务 Feign 客户端契约（统一入口：服务间调用一律经本接口，禁止业务服务自建 FeignClient 或直连他域 Mapper）。
 * <p>实现由 document-service 的受保护接口承担；调用方（如 task-service）直接注入本接口，经网关调用，
 * 当前请求 JWT 由 common-feign 默认装配的拦截器透传，身份在链路上连续、不可伪造。</p>
 * <p>契约统一 {@link Result} 封装：成功 code=0 + data；业务失败 code=业务错误码 + message（HTTP 200），
 * 调用方按 {@code Result.code} 判断，网络层异常仍抛 {@code FeignException}。</p>
 * <p>Phase 2 首个跨服务调用（审批合并 + 文档引用查询）；Phase 3 追加权限校验等契约。</p>
 */
@FeignClient(name = "document-service",
        url = "${agent-doc.feign.gateway-url:http://localhost:9090}")
public interface DocumentFeign {

    /**
     * 应用变更并合并至正式文档（校验基线版本，防并发覆盖；自动生成新版本快照）。
     *
     * @param request 合并请求
     * @return 合并结果（成功 code=0；基线版本不匹配返回 CONFLICT 40900）
     */
    @PostMapping("/api/document/documents/merge")
    Result<MergeResultVO> mergeDocument(@RequestBody MergeRequestDTO request);

    /**
     * 批量查询文档引用投影（id/spaceId/title），用于标题回填等（登录即可，标题非敏感）。
     *
     * @param documentIds 文档 ID 列表
     * @return 文档引用投影列表（成功 code=0；不存在的 ID 不返回）
     */
    @GetMapping("/api/document/documents/refs")
    Result<List<DocumentRefVO>> getDocumentRefs(@RequestParam List<Long> documentIds);

    /**
     * 查询空间下全部文档 ID（用于按空间过滤审批队列）。
     *
     * @param spaceId 空间 ID
     * @return 文档 ID 列表（成功 code=0）
     */
    @GetMapping("/api/document/documents/ids")
    Result<List<Long>> listDocumentIdsBySpace(@RequestParam Long spaceId);

    /**
     * 查询 Agent 任务执行上下文
     */
    @GetMapping("/api/document/documents/{documentId}/execution-context")
    Result<DocumentExecutionContextVO> getExecutionContext(@PathVariable Long documentId);

    /**
     * 校验当前用户在空间中的最低角色。
     */
    @GetMapping("/api/document/spaces/{spaceId}/permission")
    Result<Void> checkSpacePermission(@PathVariable Long spaceId,
                                      @RequestParam Integer minRole);

    /**
     * 查询空间 Agent 执行预算
     */
    @GetMapping("/api/document/spaces/{spaceId}/execution-budget")
    Result<SpaceBudgetVO> getSpaceExecutionBudget(
            @PathVariable Long spaceId);

    /**
     * 文档片段读取
     */
    @GetMapping("/api/document/documents/{documentId}/fragments")
    Result<DocumentFragmentVO> readFragment(@PathVariable Long documentId,
                                            @RequestParam long start, @RequestParam int length);

    /**
     * Agent 更新草稿文档
     */
    @PostMapping("/api/document/documents/draft-agent-apply")
    Result<MergeResultVO> applyDraftAgentChanges(@RequestBody MergeRequestDTO request);
}
