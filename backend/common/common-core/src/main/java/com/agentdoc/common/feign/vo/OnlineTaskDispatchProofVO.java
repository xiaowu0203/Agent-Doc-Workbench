package com.agentdoc.common.feign.vo;

import com.agentdoc.common.feign.dto.OnlineAssignmentRequestDTO;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import java.util.List;

/** Task 当前持久化身份与原动作权威证明。
 * @param request 原冻结输入
 * @param binding 原已接受绑定
 * @param taskStatus 当前 Task 状态
 * @param actions 由原文档类型确定的动作
 * @param releaseHash Task 当前进程发布身份 */
public record OnlineTaskDispatchProofVO(OnlineAssignmentRequestDTO request, OnlineTaskBindingDTO binding,
        String taskStatus, List<String> actions, String releaseHash) { }
