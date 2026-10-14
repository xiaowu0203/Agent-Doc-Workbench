package com.agentdoc.common.feign.dto;

import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import java.util.HashSet;
import java.util.List;

/** 人类报告批量查询；凭当前空间读取权限，不能作为执行授权。
 * @param spaceId 当前空间 @param experimentId 实验 @param bindings 每批至多100个已接受绑定 */
public record OnlineReportFactsQueryDTO(String spaceId, String experimentId, List<OnlineTaskBindingDTO> bindings) {
    public void validate() {
        AuthUtils.getUserIdOrException(); OnlineProtocolUtils.id(spaceId); OnlineProtocolUtils.id(experimentId);
        if (bindings == null || bindings.isEmpty() || bindings.size() > 100) { throw new IllegalArgumentException("报告事实批次无效"); }
        var ids = new HashSet<String>();
        for (var binding : bindings) {
            if (binding == null || binding.schemaVersion() != 2 || !spaceId.equals(binding.spaceId())
                    || !experimentId.equals(binding.experimentId()) || !ids.add(binding.taskId())) { throw new IllegalArgumentException("报告绑定无效"); }
            OnlineProtocolUtils.id(binding.taskId()); OnlineProtocolUtils.id(binding.assignmentId());
        }
    }
}
