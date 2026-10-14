package com.agentdoc.common.feign.vo;
import java.util.List;
/** 冻结范围的最小保护投影，无正文或配置秘密。
 * @param spaceId 所属空间 @param agentId 固定 Agent @param documentIds 冻结范围 @param emergency 紧急准入关闭 */
public record OnlineResourceScopeVO(String spaceId, String agentId, List<String> documentIds, boolean emergency) { }
