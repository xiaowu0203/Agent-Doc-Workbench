package com.agentdoc.task.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.SpaceRole;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.vo.AgentExecutionProfileVO;
import com.agentdoc.task.a2a.A2aTokenUsage;
import com.agentdoc.task.constant.TaskConstant;
import com.agentdoc.task.convertor.TokenUsageConvertor;
import com.agentdoc.task.enums.TokenUsageDimension;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.mapper.TokenUsageDetailMapper;
import com.agentdoc.task.mapper.TokenUsageMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.entity.TokenUsageDetailEntity;
import com.agentdoc.task.pojo.entity.TokenUsageEntity;
import com.agentdoc.task.pojo.vo.TokenUsageTodayVO;
import com.agentdoc.task.pojo.vo.TokenUsageTrendVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

/**
 * Token用量统计服务
 * 负责Agent任务执行过程Token消耗记录、预算管控、空间用量查询、趋势统计。
 */
@Service
@RequiredArgsConstructor
public class TokenUsageService {

    private final TokenUsageDetailMapper detailMapper;
    private final TaskMapper taskMapper;
    private final TokenUsageMapper usageMapper;
    private final DocumentFeign documentFeign;

    /**
     * 记录单次Agent执行Token消耗，同时做任务Token预算管控
     * <p>
     * 事务原子性：写入用量明细、更新任务已消耗Token，要么全部成功，要么全部回滚。
     * <ol>
     * <li>读取模型单价；保留远端返回的null，避免把“未返回”误判为0；</li>
     * <li>构建并插入{@link TokenUsageDetailEntity}执行明细记录；</li>
     * <li>累加更新任务的tokensUsed已消耗token；</li>
     * <li>判断：如果设置了任务tokenBudget预算，且累加后已消耗超过预算，则自动将任务置为TERMINATED，记录“Token预算已用尽”错误信息与结束时间；返回false表示任务已被预算终止；</li>
     * <li>未超预算返回true，任务可以继续执行。</li>
     * </ol>
     * </p>
     * @param task 当前执行的任务实体
     * @param agent 执行任务的Agent实体
     * @param result 大模型返回执行结果，包含inputTokens、cachedInputTokens、outputTokens
     * @return true：未超预算，任务允许继续执行；false：已超出Token预算，任务已被自动终止
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean recordRemote(TaskEntity task, AgentExecutionProfileVO agent, A2aTokenUsage result) {
        Long input = result.inputTokens();
        Long cachedInput = result.cachedInputTokens();
        Long output = result.outputTokens();
        Long used = input == null || output == null ? null : input + output;
        BigDecimal cost = input == null || output == null
                ? null
                : estimateCost(agent.inputPricePerMillion(), agent.outputPricePerMillion(), input, output);
        boolean estimated = result.inputTokensEstimated() || result.outputTokensEstimated();

        // 构建明细记录并入库
        TokenUsageDetailEntity detail = TokenUsageConvertor.toDetail(
                task, agent.agentId(), agent.modelId(), input, cachedInput, output,
                result.inputTokensEstimated(), result.cachedInputTokensEstimated(),
                result.outputTokensEstimated(), cost);
        detailMapper.insert(detail);

        // 输入或输出缺失时，任务总量也保持null，不能伪装成0
        taskMapper.update(null, new LambdaUpdateWrapper<TaskEntity>()
                .eq(TaskEntity::getId, task.getId())
                .set(TaskEntity::getTokensUsed, used)
                .set(TaskEntity::getTokensEstimated, estimated));
        // 更新内存对象，方便后续逻辑判断
        task.setTokensUsed(used);
        task.setTokensEstimated(estimated);

        // 只有总量可用时才进行预算判断
        if (task.getTokenBudget() != null && used != null && used > task.getTokenBudget()) {
            taskMapper.update(null, new LambdaUpdateWrapper<TaskEntity>()
                    .eq(TaskEntity::getId, task.getId())
                    .set(TaskEntity::getStatus, TaskStatus.TERMINATED.getCode())
                    .set(TaskEntity::getErrorMessage, "任务 Token 预算已用尽")
                    .set(TaskEntity::getEndTime, LocalDateTime.now()));
            task.setStatus(TaskStatus.TERMINATED.getCode());
            return false;
        }
        return true;
    }

    /**
     * 查询空间今日总消耗token数量（输入+输出合计）
     * @param spaceId 空间ID
     * @return 今日token总和，无数据返回0
     */
    public long todaySpaceTokens(Long spaceId) {
        // 校验用户空间查看权限
        requireMember(spaceId);
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(TaskConstant.DAY_OFFSET);
        Long value = detailMapper.sumTokensBySpaceAndDate(spaceId, start.toLocalDate(), end.toLocalDate());
        return value == null ? 0 : value;
    }

    /**
     * 查询空间今日Token用量统计VO：输入token、输出token、合计费用
     * @param spaceId 空间ID
     * @return 当日用量VO
     */
    public TokenUsageTodayVO today(Long spaceId) {
        // 校验用户空间查看权限
        requireMember(spaceId);
        // 计算输入、输出token
        LocalDate date = LocalDate.now();
        LocalDate end = date.plusDays(TaskConstant.DAY_OFFSET);
        Long input = detailMapper.sumInputBySpaceAndDate(spaceId, date, end);
        Long output = detailMapper.sumOutputBySpaceAndDate(spaceId, date, end);
        boolean inputUnavailable = Boolean.TRUE.equals(
                detailMapper.hasNullInputBySpaceAndDate(spaceId, date, end));
        boolean outputUnavailable = Boolean.TRUE.equals(
                detailMapper.hasNullOutputBySpaceAndDate(spaceId, date, end));
        boolean inputEstimated = !inputUnavailable && Boolean.TRUE.equals(
                detailMapper.hasEstimatedInputBySpaceAndDate(spaceId, date, end));
        boolean outputEstimated = !outputUnavailable && Boolean.TRUE.equals(
                detailMapper.hasEstimatedOutputBySpaceAndDate(spaceId, date, end));
        return TokenUsageTodayVO.of(spaceId, input, output,
                inputUnavailable || outputUnavailable ? null
                        : detailMapper.sumCostBySpaceAndDate(spaceId, date, end),
                inputUnavailable ? false : inputEstimated,
                outputUnavailable ? false : outputEstimated);
    }

    /**
     * 查询空间Token用量历史趋势
     * <p>对入参days做范围钳位，限制在最小/最大可查询天数之间；读取预聚合表{@link TokenUsageEntity}。</p>
     * @param spaceId 空间ID
     * @param days 请求查询的历史天数
     * @return 按日期升序排列的每日用量趋势列表
     */
    public List<TokenUsageTrendVO> trend(Long spaceId, int days) {
        requireMember(spaceId);
        // 规范化查询天数，防止过小/过大查询
        int normalizedDays = Math.max(TaskConstant.MIN_TREND_DAYS,
                Math.min(days, TaskConstant.MAX_TREND_DAYS));
        return usageMapper.selectList(new LambdaQueryWrapper<TokenUsageEntity>()
                        .eq(TokenUsageEntity::getSpaceId, spaceId)
                        .eq(TokenUsageEntity::getDimension, TokenUsageDimension.SPACE.getCode())
                        .eq(TokenUsageEntity::getObjId, spaceId)
                        .ge(TokenUsageEntity::getUsageDate, LocalDate.now().minusDays(normalizedDays))
                        .lt(TokenUsageEntity::getUsageDate, LocalDate.now())
                        .orderByAsc(TokenUsageEntity::getUsageDate))
                .stream().map(TokenUsageTrendVO::from).toList();
    }

    /**
     * 校验当前用户在该空间具备Viewer及以上查看权限
     * @param spaceId 空间ID
     * @throws BusinessException 权限不足或调用文档服务异常抛出业务异常
     */
    private void requireMember(Long spaceId) {
        var result = documentFeign.checkSpacePermission(spaceId, SpaceRole.VIEWER.getCode());
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "空间权限校验失败" : result.message());
        }
    }

    /**
     * 估算本次模型调用费用
     * <p>按百万token单价计算，保留{@link TaskConstant#TOKEN_COST_SCALE}位小数，四舍五入。</p>
     * @param configuredInputPrice 每百万输入token价格
     * @param configuredOutputPrice 每百万token输出价格
     * @param input 输入token数量
     * @param output 输出token数量
     * @return 估算费用；model为null返回{@link BigDecimal#ZERO}
     */
    private BigDecimal estimateCost(BigDecimal configuredInputPrice, BigDecimal configuredOutputPrice,
                                    Long input, Long output) {
        BigDecimal inputPrice = configuredInputPrice == null ? BigDecimal.ZERO : configuredInputPrice;
        BigDecimal outputPrice = configuredOutputPrice == null ? BigDecimal.ZERO : configuredOutputPrice;

        // 1.输入token × 百万输入单价 + 输出token × 百万输出单价
        // 2.除以1000000，换算真实费用；指定保留小数位数+四舍五入，避免除不尽抛出算术异常
        return inputPrice.multiply(BigDecimal.valueOf(input))
                .add(outputPrice.multiply(BigDecimal.valueOf(output)))
                .divide(BigDecimal.valueOf(TaskConstant.TOKEN_PRICE_UNIT),
                        TaskConstant.TOKEN_COST_SCALE, RoundingMode.HALF_UP);
    }
}
