package com.agentdoc.evaluation.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 分块读写与报告落库共用本领域事务；不访问其他业务域。 */
public interface OnlineReportInputChunkMapper {
    @Insert("INSERT INTO online_report_input_chunk(report_id,chunk_no,content,content_hash) VALUES(#{reportId},#{number},#{content},#{hash})")
    int insert(@Param("reportId") Long reportId, @Param("number") int number, @Param("content") String content, @Param("hash") String hash);
    @Select("SELECT chunk_no AS number,content,content_hash AS hash FROM online_report_input_chunk WHERE report_id=#{reportId} ORDER BY chunk_no")
    List<Chunk> find(Long reportId);
    /** @param number 连续序号 @param content 输入片段 @param hash 片段摘要 */
    record Chunk(int number, String content, String hash) { }
}
