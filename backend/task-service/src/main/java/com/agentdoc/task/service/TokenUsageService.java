package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.AgentToolUsageQueryDTO;
import com.agentdoc.common.feign.vo.AgentExecutionProfileVO;
import com.agentdoc.common.feign.vo.AgentToolUsageStatsVO;
import com.agentdoc.task.a2a.A2aTokenUsage;
import com.agentdoc.task.constant.TaskConstant;
import com.agentdoc.task.convertor.TokenUsageConvertor;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.enums.TokenUsageDimension;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.mapper.TokenUsageDetailMapper;
import com.agentdoc.task.mapper.TokenUsageMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.entity.TokenUsageDetailEntity;
import com.agentdoc.task.pojo.entity.TokenUsageEntity;
import com.agentdoc.task.pojo.param.TokenUsageDashboardParam;
import com.agentdoc.task.pojo.vo.MonthlyTokenBudgetVO;
import com.agentdoc.task.pojo.vo.TokenUsageDailyRow;
import com.agentdoc.task.pojo.vo.TokenUsageDailyVO;
import com.agentdoc.task.pojo.vo.TokenUsageDashboardVO;
import com.agentdoc.task.pojo.vo.TokenUsageStatisticsRow;
import com.agentdoc.task.pojo.vo.TokenUsageSummaryVO;
import com.agentdoc.task.pojo.vo.TokenUsageTodayVO;
import com.agentdoc.task.pojo.vo.TokenUsageTrendVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.USAGE_READ;
import static com.agentdoc.task.constant.TaskConstant.TOKEN_USAGE_TIME_ZONE;

/**
 * Token用量统计服务
 * 负责Agent任务执行过程Token消耗记录、预算管控、空间用量查询、趋势统计。
 * 核心能力：
 * 1. 任务执行Token明细落库，任务级Token预算熔断终止任务
 * 2. 空间维度：今日用量、本月预算用量、历史趋势图表数据
 * 3. 用量大盘Dashboard：周期汇总、环比上周期、每日趋势、工具调用分布
 * 4. 权限校验、Feign远程调用封装、模型费用估算
 */
@Service
@RequiredArgsConstructor
public class TokenUsageService {

    // Token执行明细Mapper：存储每一次模型调用的token明细记录
    private final TokenUsageDetailMapper detailMapper;
    // 任务Mapper：更新任务实体的token消耗、任务状态（预算超限终止）
    private final TaskMapper taskMapper;
    // Token聚合统计Mapper：预聚合表，用于查询历史趋势
    private final TokenUsageMapper usageMapper;
    // 文档Feign：空间权限校验、获取空间月度Token预算配置
    private final DocumentFeign documentFeign;
    // AgentFeign：远程获取工具调用统计数据
    private final AgentFeign agentFeign;

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
        // 获取模型返回的输入token、缓存输入token、输出token
        Long input = result.inputTokens();
        Long cachedInput = result.cachedInputTokens();
        Long output = result.outputTokens();

        // 计算本次总消耗token：输入+输出；任一为null则总消耗为null，不强行填充0
        Long used = input == null || output == null ? null : input + output;

        // 估算本次调用成本，输入输出任意为空则成本为null
        BigDecimal cost = input == null || output == null
                ? null
                : estimateCost(agent.inputPricePerMillion(), agent.outputPricePerMillion(), input, output);

        // 是否为估算token（模型返回的是预估数值，不是真实精确token）
        boolean estimated = result.inputTokensEstimated() || result.outputTokensEstimated();

        // 构建Token明细实体，插入明细记录表
        TokenUsageDetailEntity detail = TokenUsageConvertor.toDetail(
                task, agent.agentId(), agent.modelId(), input, cachedInput, output,
                result.inputTokensEstimated(), result.cachedInputTokensEstimated(),
                result.outputTokensEstimated(), cost);
        detailMapper.insert(detail);

        // 更新任务的已消耗token、是否预估标记；输入输出缺失时保持null，不伪装成0
        taskMapper.update(null, new LambdaUpdateWrapper<TaskEntity>()
                .eq(TaskEntity::getId, task.getId())
                .set(TaskEntity::getTokensUsed, used)
                .set(TaskEntity::getTokensEstimated, estimated));

        // 更新内存中task对象，供后续业务逻辑直接使用，避免再次查库
        task.setTokensUsed(used);
        task.setTokensEstimated(estimated);

        // 仅当任务配置了预算、且本次总消耗token不为null，才执行预算判断
        if (task.getTokenBudget() != null && used != null && used > task.getTokenBudget()) {
            // 预算超限：修改任务状态为终止，写入错误提示、记录结束时间
            taskMapper.update(null, new LambdaUpdateWrapper<TaskEntity>()
                    .eq(TaskEntity::getId, task.getId())
                    .set(TaskEntity::getStatus, TaskStatus.TERMINATED.getCode())
                    .set(TaskEntity::getErrorMessage, "任务 Token 预算已用尽")
                    .set(TaskEntity::getEndTime, LocalDateTime.now()));
            // 更新内存任务状态
            task.setStatus(TaskStatus.TERMINATED.getCode());
            // 返回false：任务被预算熔断，禁止继续执行
            return false;
        }
        // 未超限，任务可以继续运行
        return true;
    }

    /**
     * 查询空间今日总消耗token数量（输入+输出合计）
     * @param spaceId 空间ID
     * @return 今日token总和，无数据返回0
     */
    public long todaySpaceTokens(Long spaceId) {
        // 校验当前用户拥有该空间查看权限
        requireMember(spaceId);
        // 今日0点开始时间
        LocalDateTime start = LocalDate.now().atStartOfDay();
        // 今日结束时间（跨天偏移）
        LocalDateTime end = start.plusDays(TaskConstant.DAY_OFFSET);
        // 查询明细表里该空间今日token合计
        Long value = detailMapper.sumTokensBySpaceAndDate(spaceId, start.toLocalDate(), end.toLocalDate());
        return value == null ? 0 : value;
    }

    /**
     * 查询空间今日Token用量统计VO：输入token、输出token、合计费用
     * @param spaceId 空间ID
     * @return 当日用量VO
     */
    public TokenUsageTodayVO today(Long spaceId) {
        // 校验空间查看权限
        requireMember(spaceId);
        LocalDate date = LocalDate.now();
        LocalDate end = date.plusDays(TaskConstant.DAY_OFFSET);

        // 统计今日输入、输出token总和
        Long input = detailMapper.sumInputBySpaceAndDate(spaceId, date, end);
        Long output = detailMapper.sumOutputBySpaceAndDate(spaceId, date, end);

        // 判断是否存在缺失数据：输入token为null、输出token为null、成本为null
        boolean inputUnavailable = Boolean.TRUE.equals(
                detailMapper.hasNullInputBySpaceAndDate(spaceId, date, end));
        boolean outputUnavailable = Boolean.TRUE.equals(
                detailMapper.hasNullOutputBySpaceAndDate(spaceId, date, end));
        boolean costUnavailable = Boolean.TRUE.equals(
                detailMapper.hasNullCostBySpaceAndDate(spaceId, date, end));

        // 判断是否存在预估token记录
        boolean inputEstimated = !inputUnavailable && Boolean.TRUE.equals(
                detailMapper.hasEstimatedInputBySpaceAndDate(spaceId, date, end));
        boolean outputEstimated = !outputUnavailable && Boolean.TRUE.equals(
                detailMapper.hasEstimatedOutputBySpaceAndDate(spaceId, date, end));

        // 组装返回VO，存在缺失数据时成本置null
        return TokenUsageTodayVO.of(spaceId, input, output,
                inputUnavailable || outputUnavailable || costUnavailable ? null
                        : detailMapper.sumCostBySpaceAndDate(spaceId, date, end),
                inputUnavailable ? false : inputEstimated,
                outputUnavailable ? false : outputEstimated);
    }

    /**
     * 查询空间本月 Token 用量与空间预算。
     *
     * @param spaceId 空间 ID
     * @return 两个原始数值，使用率由前端计算
     */
    public MonthlyTokenBudgetVO monthly(Long spaceId) {
        requireMember(spaceId);
        // 本月1号作为统计起始
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        // 统计本月已消耗token
        Long used = detailMapper.sumTokensBySpaceAndDate(spaceId, start, start.plusMonths(1));
        // 远程调用文档服务获取空间配置的月度token预算
        Long budget = requireData(documentFeign.getSpaceTokenBudget(spaceId)).monthlyTokenBudget();
        return new MonthlyTokenBudgetVO(used == null ? 0 : used, budget);
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
        // 限制查询天数，防止传入过小/过大参数，避免查询性能问题
        int normalizedDays = Math.max(TaskConstant.MIN_TREND_DAYS,
                Math.min(days, TaskConstant.MAX_TREND_DAYS));
        // 查询预聚合表，按空间、维度、对象id、日期范围查询，日期升序
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
     * 查询用量页所需的周期汇总、上周期对比、连续每日趋势和工具来源分布。
     * @param param 仪表盘查询参数：空间、时间范围、agentId、modelId、任务状态过滤
     * @return 用量大盘完整VO
     */
    public TokenUsageDashboardVO dashboard(TokenUsageDashboardParam param) {
        // 参数合法性校验
        param.validate();
        // 校验空间查看权限
        requireMember(param.spaceId());
        // 用量统计时区
        ZoneId zoneId = ZoneId.of(TOKEN_USAGE_TIME_ZONE);
        // 当前查询周期开始、结束时间
        LocalDateTime start = param.startDate().atStartOfDay();
        LocalDateTime end = param.endDate().plusDays(TaskConstant.DAY_OFFSET).atStartOfDay();

        // 计算周期天数，用于计算上一个对等周期时间范围
        long periodDays = ChronoUnit.DAYS.between(param.startDate(), param.endDate()) + 1;
        LocalDateTime previousStart = param.startDate().minusDays(periodDays).atStartOfDay();
        LocalDateTime previousEnd = start;

        // 任务状态转换为数据库查询条件
        Integer taskStatus = param.status() == null ? null : param.status().getCode();

        // 查询当前周期统计汇总
        TokenUsageStatisticsRow currentRow = detailMapper.summarize(
                param.spaceId(), start, end, param.agentId(), param.modelId(), taskStatus);
        // 查询上一周期统计汇总，用于环比对比
        TokenUsageStatisticsRow previousRow = detailMapper.summarize(
                param.spaceId(), previousStart, previousEnd, param.agentId(), param.modelId(), taskStatus);

        // 当前周期工具调用统计
        AgentToolUsageStatsVO currentTools = toolUsage(param, start, end);
        // 上一周期工具调用统计
        AgentToolUsageStatsVO previousTools = toolUsage(param, previousStart, previousEnd);

        // 按日期分组每日明细数据
        Map<LocalDate, TokenUsageDailyRow> dailyRows = detailMapper.summarizeDaily(
                        param.spaceId(), start, end, param.agentId(), param.modelId(), taskStatus)
                .stream().collect(Collectors.toMap(TokenUsageDailyRow::usageDate, Function.identity()));

        // 遍历时间区间，生成每日趋势VO；没有数据的日期也填充空数据
        List<TokenUsageDailyVO> trend = param.startDate().datesUntil(
                        param.endDate().plusDays(TaskConstant.DAY_OFFSET))
                .map(date -> daily(date, dailyRows.get(date)))
                .toList();

        // 获取本月预算数据
        MonthlyTokenBudgetVO monthly = monthly(param.spaceId());

        // 组装大盘返回对象
        return new TokenUsageDashboardVO(param.startDate(), param.endDate(), LocalDateTime.now(zoneId),
                TOKEN_USAGE_TIME_ZONE, summary(currentRow, currentTools.totalCalls()),
                summary(previousRow, previousTools.totalCalls()), trend, currentTools.sources(),
                monthly.usedTokens(), monthly.monthlyTokenBudget());
    }

    /**
     * 远程调用Agent服务，获取指定时间范围工具调用统计
     * @param param 仪表盘入参
     * @param start 统计开始时间
     * @param end 统计结束时间
     * @return 工具调用统计VO
     */
    private AgentToolUsageStatsVO toolUsage(TokenUsageDashboardParam param,
                                            LocalDateTime start, LocalDateTime end) {
        Result<AgentToolUsageStatsVO> result = agentFeign.getToolUsageStats(new AgentToolUsageQueryDTO(
                param.spaceId(), start, end, param.agentId(), param.modelId(),
                executionStatus(param.status())));
        // 校验feign返回结果，异常抛出业务异常
        return requireData(result);
    }

    /**
     * 任务状态枚举转换为Agent服务需要的字符串状态码
     * @param status 任务状态
     * @return Agent服务识别的状态字符串
     */
    private String executionStatus(TaskStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PENDING -> "NOT_STARTED";
            case DISPATCHED -> "SUBMITTED";
            case RUNNING, CANCELING -> "WORKING";
            case WAITING_INPUT -> "INPUT_REQUIRED";
            case WAITING_AUTH -> "AUTH_REQUIRED";
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
            case TERMINATED -> "CANCELED";
        };
    }

    /**
     * 将数据库统计行转换为用量汇总VO
     * @param row 数据库聚合统计行
     * @param toolCalls 工具总调用次数
     * @return 汇总VO
     */
    private TokenUsageSummaryVO summary(TokenUsageStatisticsRow row, long toolCalls) {
        // 判断是否存在有效统计数据
        boolean hasData = row != null && positive(row.recordCount());
        if (!hasData) {
            // 无数据返回空汇总对象
            return new TokenUsageSummaryVO(false, null, null, null, null,
                    0, toolCalls, false, false, false);
        }
        Long input = row.inputTokens();
        Long output = row.outputTokens();
        // 合计token：输入+输出，任一null则为null
        Long tokens = input == null || output == null ? null : input + output;
        BigDecimal cost = row.estimatedCost();
        return new TokenUsageSummaryVO(true, input, output, tokens, cost,
                value(row.taskCount()), toolCalls, positive(row.estimatedInputCount()),
                positive(row.estimatedOutputCount()),
                positive(row.missingInputCount()) || positive(row.missingOutputCount())
                        || positive(row.missingCostCount()));
    }

    /**
     * 组装单天用量VO
     * @param date 日期
     * @param row 当日数据库统计行
     * @return 每日用量VO
     */
    private TokenUsageDailyVO daily(LocalDate date, TokenUsageDailyRow row) {
        if (row == null || !positive(row.recordCount())) {
            // 无记录返回空数据对象
            return new TokenUsageDailyVO(date, false, 0L, 0L, 0L,
                    BigDecimal.ZERO, false, false, false);
        }
        Long input = row.inputTokens();
        Long output = row.outputTokens();
        return new TokenUsageDailyVO(date, true, input, output,
                input == null || output == null ? null : input + output,
                row.estimatedCost(), positive(row.estimatedInputCount()), positive(row.estimatedOutputCount()),
                positive(row.missingInputCount()) || positive(row.missingOutputCount())
                        || positive(row.missingCostCount()));
    }

    /**
     * 判断数值是否大于0
     * @param value 待判断Long
     * @return true：不为null且>0
     */
    private boolean positive(Long value) {
        return value != null && value > 0;
    }

    /**
     * Long空值安全转换为long，null返回0
     * @param value Long值
     * @return long
     */
    private long value(Long value) {
        return value == null ? 0 : value;
    }

    /**
     * 校验当前用户在该空间具备Viewer及以上查看权限
     * @param spaceId 空间ID
     * @throws BusinessException 权限不足或调用文档服务异常抛出业务异常
     */
    private void requireMember(Long spaceId) {
        var result = documentFeign.checkSpacePermission(spaceId, USAGE_READ);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "空间权限校验失败" : result.message());
        }
    }

    /**
     * Feign 返回结果包装工具，非成功或无 data 时抛出业务异常。
     *
     * @param result Feign 远程调用结果
     * @param <T> data 类型
     * @return 远程结果中的 data
     */
    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "文档服务调用失败" : result.message());
        }
        return result.data();
    }

    /**
     * 估算本次模型调用费用
     * <p>按百万token单价计算，保留{@link TaskConstant#TOKEN_COST_SCALE}位小数，四舍五入。</p>
     * @param configuredInputPrice 每百万输入token价格
     * @param configuredOutputPrice 每百万输出token价格
     * @param input 输入token数量
     * @param output 输出token数量
     * @return 估算费用；任一单价未配置时返回 null
     */
    private BigDecimal estimateCost(BigDecimal configuredInputPrice, BigDecimal configuredOutputPrice,
                                    Long input, Long output) {
        // 单价任意为null，无法计算成本，直接返回null
        if (configuredInputPrice == null || configuredOutputPrice == null) {
            return null;
        }

        // 1.输入token × 百万输入单价 + 输出token × 百万输出单价
        // 2.除以1000000，换算真实费用；指定保留小数位数+四舍五入，避免除不尽抛出算术异常
        return configuredInputPrice.multiply(BigDecimal.valueOf(input))
                .add(configuredOutputPrice.multiply(BigDecimal.valueOf(output)))
                .divide(BigDecimal.valueOf(TaskConstant.TOKEN_PRICE_UNIT),
                        TaskConstant.TOKEN_COST_SCALE, RoundingMode.HALF_UP);
    }
}
