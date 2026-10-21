package com.agentdoc.evaluation.mapper;
import com.agentdoc.evaluation.pojo.entity.EvaluatorVersionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.agentdoc.evaluation.pojo.vo.CatalogVersionSummaryVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface EvaluatorVersionMapper extends BaseMapper<EvaluatorVersionEntity> {
    @Select("""
            <script>
            SELECT s.resource_id, s.version_count, s.draft_count, p.id AS latest_published_version_id,
                   p.version_no AS latest_published_version_no,
                   CASE WHEN p.id IS NULL THEN NULL ELSE (SELECT COUNT(DISTINCT b.test_case_version_id) FROM test_case_evaluator b JOIN evaluation_test_case_version c ON c.id=b.test_case_version_id WHERE b.evaluator_version_id=p.id AND c.space_id=#{spaceId}) END AS published_binding_count
            FROM (
                SELECT evaluator_id AS resource_id, COUNT(*) AS version_count,
                       SUM(CASE WHEN status='DRAFT' THEN 1 ELSE 0 END) AS draft_count,
                       MAX(CASE WHEN status='PUBLISHED' THEN version_no END) AS latest_no
                FROM evaluator_version WHERE space_id=#{spaceId} AND evaluator_id IN
                <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
                GROUP BY evaluator_id
            ) s
            LEFT JOIN evaluator_version p ON p.evaluator_id=s.resource_id AND p.space_id=#{spaceId}
                AND p.version_no=s.latest_no AND p.status='PUBLISHED'
            </script>
            """)
    List<CatalogVersionSummaryVO> resourceSummaries(@Param("spaceId") Long spaceId, @Param("ids") List<Long> ids);
}
