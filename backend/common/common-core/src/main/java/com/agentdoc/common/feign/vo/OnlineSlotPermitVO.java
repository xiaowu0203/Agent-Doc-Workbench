package com.agentdoc.common.feign.vo;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
/** 权威槽许可，开始时须再次向 Evaluation 核验。
 * @param binding 冻结绑定 @param bindingHash 绑定摘要 @param generation 槽代次
 * @param permitHash 当前许可 @param started 是否已线性化开始 */
public record OnlineSlotPermitVO(OnlineTaskBindingDTO binding, String bindingHash, long generation,
        String permitHash, boolean started) { }
