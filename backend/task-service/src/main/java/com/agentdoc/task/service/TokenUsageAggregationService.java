package com.agentdoc.task.service;

import com.agentdoc.task.constant.TaskConstant;
import com.agentdoc.task.convertor.TokenUsageConvertor;
import com.agentdoc.task.enums.TokenSnapshotType;
import com.agentdoc.task.enums.TokenUsageDimension;
import com.agentdoc.task.mapper.TokenDailySnapshotMapper;
import com.agentdoc.task.mapper.TokenUsageDetailMapper;
import com.agentdoc.task.mapper.TokenUsageMapper;
import com.agentdoc.task.pojo.vo.TokenUsageAggregateRow;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Token用量聚合快照服务。
 */
@Service
@RequiredArgsConstructor
public class TokenUsageAggregationService {

    private final TokenUsageDetailMapper detailMapper;
    private final TokenUsageMapper usageMapper;
    private final TokenDailySnapshotMapper snapshotMapper;

    /**
     * 定时任务：聚合昨日全量多维度统计数据
     * <p>
     * cron配置可配置，默认每日 00:00(上海时区)执行。
     * </p>
     */
    @Scheduled(cron = "${agent-doc.token-usage.aggregate-cron:0 0 0 * * *}", zone = "Asia/Shanghai")
    @Transactional(rollbackFor = Exception.class)
    public void aggregateYesterday() {
        aggregate(LocalDate.now().minusDays(TaskConstant.DAY_OFFSET));
    }

    /**
     * 定时任务：生成当日各空间实时用量快照
     * <p>
     * 固定间隔轮询，间隔可配置；扫描今天有数据的全部空间，从明细表实时汇总 input/output/cost，写入快照表。
     * 快照只做【空间维度】当日累计快照；不做文档/Agent/任务维度。
     * 注意：该方法不加事务，每条快照独立入库；快照是采样记录，允许少量丢失。
     * </p>
     */
    @Scheduled(fixedDelayString = "${agent-doc.token-usage.snapshot-delay-ms:"
            + TaskConstant.DEFAULT_SNAPSHOT_DELAY_MILLIS + "}")
    public void snapshotToday() {
        LocalDate date = LocalDate.now();
        LocalDate end = date.plusDays(TaskConstant.DAY_OFFSET);
        // 查询今天产生过token消耗的全部spaceId
        for (Long spaceId : detailMapper.listSpacesByDate(date, end)) {
            snapshot(spaceId, date,
                    detailMapper.sumInputBySpaceAndDate(spaceId, date, end),
                    detailMapper.sumOutputBySpaceAndDate(spaceId, date, end),
                    detailMapper.sumCostBySpaceAndDate(spaceId, date, end));
        }
    }

    /**
     * 对指定日期执行多维度聚合
     * <p>
     * 重写模式：先删除该日期已存在的全部聚合数据；
     * 再分别聚合：SPACE（空间）、DOCUMENT（文档）、TASK（任务）、AGENT（Agent），写入token_usage聚合表。
     * </p>
     * @param date 需要聚合的业务日期
     */
    @Transactional(rollbackFor = Exception.class)
    public void aggregate(LocalDate date) {
        LocalDate end = date.plusDays(TaskConstant.DAY_OFFSET);
        // 删除该日期旧聚合，重算覆盖，保证最终一致性
        usageMapper.deleteByUsageDate(date);
        insert(date, detailMapper.aggregateSpace(date, end), TokenUsageDimension.SPACE);
        insert(date, detailMapper.aggregateDocument(date, end), TokenUsageDimension.DOCUMENT);
        insert(date, detailMapper.aggregateTask(date, end), TokenUsageDimension.TASK);
        insert(date, detailMapper.aggregateAgent(date, end), TokenUsageDimension.AGENT);
    }

    /**
     * 插入一条空间当日用量快照记录
     * @param spaceId 空间ID
     * @param date 快照所属日期
     * @param input 累计输入token
     * @param output 累计输出token
     * @param cost 累计费用
     */
    public void snapshot(Long spaceId, LocalDate date, Long input, Long output, BigDecimal cost) {
        snapshotMapper.insert(TokenUsageConvertor.toSnapshot(
                spaceId, date, input, output, cost, TokenSnapshotType.SYSTEM));
    }

    /**
     * 将聚合行集合转换实体并批量写入聚合表
     * @param date 统计日期
     * @param rows 数据库查询出来的聚合原始行
     * @param dimension 聚合维度：SPACE / DOCUMENT / TASK / AGENT
     */
    private void insert(LocalDate date, List<TokenUsageAggregateRow> rows, TokenUsageDimension dimension) {
        for (TokenUsageAggregateRow row : rows) {
            usageMapper.insert(TokenUsageConvertor.toAggregate(row, date, dimension));
        }
    }
}
