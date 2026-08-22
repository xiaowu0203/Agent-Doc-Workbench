package com.agentdoc.common.id;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowflakeIdGeneratorTest {

    @Test
    void singleThreadGeneratesMonotonicUniqueIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        long previous = 0L;
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            long id = generator.nextId();
            assertTrue(id > 0, "ID 必须为正数");
            assertTrue(id > previous, "单线程内 ID 必须单调递增");
            assertTrue(seen.add(id), "ID 必须唯一");
            previous = id;
        }
    }

    @Test
    void concurrentGenerationIsUnique() throws InterruptedException {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(2, 2);
        int threads = 8;
        int perThread = 20_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Set<Long> seen = Collections.synchronizedSet(new HashSet<>());
        CountDownLatch latch = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        seen.add(generator.nextId());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(30, TimeUnit.SECONDS), "并发任务应全部完成");
        pool.shutdown();
        assertEquals(threads * perThread, seen.size(), "并发下 ID 不能重复");
    }

    @Test
    void rejectsInvalidWorkerId() {
        boolean thrown = false;
        try {
            new SnowflakeIdGenerator(32, 0);
        } catch (IllegalArgumentException ex) {
            thrown = true;
        }
        assertTrue(thrown, "workerId 超过 31 应抛出异常");
    }
}