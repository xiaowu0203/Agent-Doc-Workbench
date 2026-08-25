package com.agentdoc.task.mapper;

import com.agentdoc.task.pojo.entity.TokenUsageDetailEntity;
import com.agentdoc.task.pojo.vo.TokenUsageAggregateRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Token 原始消耗明细 Mapper。
 */
public interface TokenUsageDetailMapper extends BaseMapper<TokenUsageDetailEntity> {

    Long sumTokensBySpaceAndDate(@Param("spaceId") Long spaceId,
                                 @Param("start") LocalDate start,
                                 @Param("end") LocalDate end);

    Long sumInputBySpaceAndDate(@Param("spaceId") Long spaceId,
                                @Param("start") LocalDate start,
                                @Param("end") LocalDate end);

    Long sumOutputBySpaceAndDate(@Param("spaceId") Long spaceId,
                                 @Param("start") LocalDate start,
                                 @Param("end") LocalDate end);

    Boolean hasNullInputBySpaceAndDate(@Param("spaceId") Long spaceId,
                                       @Param("start") LocalDate start,
                                       @Param("end") LocalDate end);

    Boolean hasNullOutputBySpaceAndDate(@Param("spaceId") Long spaceId,
                                        @Param("start") LocalDate start,
                                        @Param("end") LocalDate end);

    Boolean hasEstimatedInputBySpaceAndDate(@Param("spaceId") Long spaceId,
                                            @Param("start") LocalDate start,
                                            @Param("end") LocalDate end);

    Boolean hasEstimatedOutputBySpaceAndDate(@Param("spaceId") Long spaceId,
                                             @Param("start") LocalDate start,
                                             @Param("end") LocalDate end);

    BigDecimal sumCostBySpaceAndDate(@Param("spaceId") Long spaceId,
                                     @Param("start") LocalDate start,
                                     @Param("end") LocalDate end);

    List<Long> listSpacesByDate(@Param("start") LocalDate start,
                                @Param("end") LocalDate end);

    List<TokenUsageAggregateRow> aggregateSpace(@Param("start") LocalDate start,
                                                @Param("end") LocalDate end);

    List<TokenUsageAggregateRow> aggregateDocument(@Param("start") LocalDate start,
                                                   @Param("end") LocalDate end);

    List<TokenUsageAggregateRow> aggregateTask(@Param("start") LocalDate start,
                                               @Param("end") LocalDate end);

    List<TokenUsageAggregateRow> aggregateAgent(@Param("start") LocalDate start,
                                                @Param("end") LocalDate end);
}
