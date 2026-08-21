package com.agentdoc.common.id;

/**
 * Snowflake 雪花ID生成器（适配数据未入库、需要先拿ID业务）
 * <p>
 * ID 64bit结构：
 * <ul>
 * <li>符号位：1bit，固定0</li>
 * <li>时间戳：41bit，相对于 {@link #EPOCH} 的毫秒数</li>
 * <li>数据中心ID：5bit，范围 [0,31]</li>
 * <li>机器工作ID：5bit，范围 [0,31]</li>
 * <li>序列号：12bit，同一毫秒内自增，单毫秒最多生成4096个ID</li>
 * </ul>
 * <p>
 * 注意：
 * <ol>
 * <li>线程安全：{@link #nextId()} 使用 synchronized 同步；</li>
 * <li>时钟回拨：检测到系统时钟回拨直接抛出异常，不做等待补偿；</li>
 * <li>workerId + datacenterId 需要集群各实例唯一，否则会产生ID重复；</li>
 * <li>默认无参构造使用 workerId=0, datacenterId=0，仅适合单机测试，集群环境必须传入真实唯一编号。</li>
 * </ol>
 */
public class SnowflakeIdGenerator {

    /**
     * 雪花算法纪元时间：2010‑11‑04 09:42:54.657 UTC
     * 时间戳部分为当前毫秒与此纪元的差值。
     */
    private static final long EPOCH = 1288834974657L;

    /** 序列号占用bit数 */
    private static final long SEQUENCE_BITS = 12L;
    /** 工作机器ID占用bit数 */
    private static final long WORKER_BITS = 5L;
    /** 数据中心ID占用bit数 */
    private static final long DATACENTER_BITS = 5L;
    /** 工作机器ID最大值 0~31 */
    private static final long MAX_WORKER_ID = -1L ^ (-1L << WORKER_BITS);
    /** 数据中心ID最大值 0~31 */
    private static final long MAX_DATACENTER_ID = -1L ^ (-1L << DATACENTER_BITS);
    /** workerId 左移位数：序列号位宽 */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    /** datacenterId 左移位数：序列号 + workerId位宽 */
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_BITS;
    /** 时间戳左移位数：序列号 + workerId + datacenterId位宽 */
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS + DATACENTER_BITS;
    /** 序列号掩码，用于序列号自增后取模，保证不越界 */
    private static final long SEQUENCE_MASK = -1L ^ (-1L << SEQUENCE_BITS);

    /** 工作机器ID(0~31)，集群实例之间必须唯一 */
    private final long workerId;
    /** 数据中心ID(0~31)，多机房场景区分机房 */
    private final long datacenterId;
    /** 同一毫秒内序列号，0‑4095循环 */
    private long sequence = 0L;
    /** 上一次生成ID的时间戳(毫秒) */
    private long lastTimestamp = -1L;

    /**
     * 构造雪花ID生成器
     * @param workerId 工作机器ID [0,31]，集群各实例需要保证不重复
     * @param datacenterId 数据中心ID [0,31]
     * @throws IllegalArgumentException workerId / datacenterId 超出合法范围抛出
     */
    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("workerId 超出范围: " + workerId);
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId 超出范围: " + datacenterId);
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    /**
     * 无参构造，workerId=0，datacenterId=0
     * <strong>仅单机测试使用，集群环境禁止直接使用，会产生ID重复</strong>
     */
    public SnowflakeIdGenerator() {
        this(0L, 0L);
    }

    /**
     * 生成下一个雪花ID，线程同步
     * @return 雪花唯一ID
     * @throws IllegalStateException 检测到系统时钟回拨时抛出
     */
    public synchronized long nextId() {
        long timestamp = currentTime();
        // 时钟回拨校验：当前时间小于上一次ID时间戳，拒绝生成ID
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("时钟回拨，拒绝生成 ID，回拨量: " + (lastTimestamp - timestamp));
        }
        // 同一毫秒内，序列号自增
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            // 序列号溢出，阻塞等到下一毫秒
            if (sequence == 0L) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // 进入新毫秒，序列号重置为0
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        // 按位拼接各部分生成最终ID
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 循环等待，直到获取大于 lastTimestamp 的新时间戳
     * @param lastTimestamp 上一次ID生成时间戳
     * @return 新的毫秒时间戳
     */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTime();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTime();
        }
        return timestamp;
    }

    /**
     * 获取当前系统毫秒时间，protected 便于单元测试mock时间
     * @return 当前毫秒时间戳
     */
    protected long currentTime() {
        return System.currentTimeMillis();
    }
}