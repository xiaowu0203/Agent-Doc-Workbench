package com.agentdoc.evaluation.mapper;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetVersionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.agentdoc.evaluation.pojo.vo.CatalogVersionSummaryVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface EvaluationDatasetVersionMapper extends BaseMapper<EvaluationDatasetVersionEntity> {
    @Select("""
            <script>
            SELECT s.resource_id, s.version_count, s.draft_count, p.id AS latest_published_version_id,
                   p.version_no AS latest_published_version_no,
                   CASE WHEN p.id IS NULL THEN NULL ELSE (SELECT COUNT(*) FROM evaluation_dataset_case b WHERE b.dataset_version_id=p.id AND b.enabled=1) END AS published_binding_count
            FROM (
                SELECT dataset_id AS resource_id, COUNT(*) AS version_count,
                       SUM(CASE WHEN status='DRAFT' THEN 1 ELSE 0 END) AS draft_count,
                       MAX(CASE WHEN status='PUBLISHED' THEN version_no END) AS latest_no
                FROM evaluation_dataset_version WHERE space_id=#{spaceId} AND dataset_id IN
                <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
                GROUP BY dataset_id
            ) s
            LEFT JOIN evaluation_dataset_version p ON p.dataset_id=s.resource_id AND p.space_id=#{spaceId}
                AND p.version_no=s.latest_no AND p.status='PUBLISHED'
            </script>
            """)
    List<CatalogVersionSummaryVO> resourceSummaries(@Param("spaceId") Long spaceId, @Param("ids") List<Long> ids);
}
