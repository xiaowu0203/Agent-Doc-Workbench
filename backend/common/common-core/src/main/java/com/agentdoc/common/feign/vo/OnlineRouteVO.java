package com.agentdoc.common.feign.vo;

import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;

/** 意图的不可变路由结果。
 * @param binding 已接受绑定；不参与为空
 * @param reason 明确不参与原因；参与为空 */
public record OnlineRouteVO(OnlineTaskBindingDTO binding, String reason) { }
