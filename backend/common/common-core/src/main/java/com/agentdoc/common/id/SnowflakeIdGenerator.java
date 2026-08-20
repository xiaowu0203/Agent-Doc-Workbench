package com.agentdoc.common.id;

/**
 * 雪花 ID 生成器（64 位）。
 * 结构：1 位符号位 + 41 位毫秒时间戳 + 5 位数据中心 + 5 位机器 + 12 位序列号。
 * 线程安全，单 JVM 内序列号自增。
 */
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1288834974657L; // 2010-11-04 起始

    private static final long SEQUENCE_BITS = 12L;
    private static final long WORKER_BITS = 5L;
    private static final long DATACENTER_BITS = 5L;

    private static final long MAX_WORKER_ID = -1L ^ (-1L << WORKER_BITS);
    private static final long MAX_DATACENTER_ID = -1L ^ (-1L << DATACENTER_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS + DATACENTER_BITS;

    private static final long SEQUENCE_MASK = -1L ^ (-1L << SEQUENCE_BITS);

    private final long workerId;
    private final long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

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

    public SnowflakeIdGenerator() {
        this(0L, 0L);
    }

    public synchronized long nextId() {
        long timestamp = currentTime();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("时钟回拨，拒绝生成 ID，回拨量: " + (lastTimestamp - timestamp));
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0L) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTime();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTime();
        }
        return timestamp;
    }

    protected long currentTime() {
        return System.currentTimeMillis();
    }
}