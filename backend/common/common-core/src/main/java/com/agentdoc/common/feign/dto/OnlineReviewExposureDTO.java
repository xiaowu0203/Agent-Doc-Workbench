package com.agentdoc.common.feign.dto;

import java.util.List;
/** 人类已读取关联资源的保守知情标记；actor只能取当前JWT，不能申请盲态。
 * @param spaceId 当前空间 @param taskIds 已返回元数据的Task @param experimentIds 已返回汇总的实验 */
public record OnlineReviewExposureDTO(String spaceId, List<String> taskIds, List<String> experimentIds) { }
