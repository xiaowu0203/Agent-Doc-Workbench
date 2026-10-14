package com.agentdoc.common.feign.dto;

import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import java.util.List;

/** CONTROL 持有者申请同范围续签或既存 Task 集合凭证。 */
public record OnlineCapabilityIssueDTO(
        /** 固定签发用途。 */ OnlineCapabilityPurpose purpose,
        /** CONTROL 续签为空；观察/取消为既存 Task 十进制身份。 */ List<String> taskIds) { }
