package com.agentdoc.evaluation.service;

import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.OnlineAssignmentRequestDTO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/** 独立消费者 JVM 的受控长任务替身。只使用拥有的隔离库，不调用真实模型。 */
public final class OnlineExecutionProcessFixture {
    public static void main(String[] args) throws Exception {
        try (var context = new AnnotationConfigApplicationContext(OnlineExecutionAuthorityMySqlTest.Config.class);
                var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            IdWorker.initSequence(Long.parseLong(args[0]), 1);
            var authority = context.getBean(OnlineExecutionAuthority.class);
            var assignments = context.getBean(OnlineAssignmentMapper.class);
            var jdbc = new JdbcTemplate(context.getBean(DriverManagerDataSource.class));
            Map<Long, CountDownLatch> active = new ConcurrentHashMap<>();
            Map<Long, CountDownLatch> finished = new ConcurrentHashMap<>();
            reply(Map.of("ready", true, "pid", ProcessHandle.current().pid()));
            for (String line; (line = input.readLine()) != null;) {
                var command = JsonUtils.parseStrict(line, JsonNode.class); String type = command.path("type").asText();
                try {
                    long experiment = command.path("experiment").asLong(); long task = command.path("task").asLong();
                    switch (type) {
                        case "ALLOCATE" -> {
                            var request = JsonUtils.parseStrict(command.path("request").toString(), OnlineAssignmentRequestDTO.class);
                            long start = System.nanoTime(); var result = authority.allocate(experiment, request);
                            reply(Map.of("accepted", result.binding() != null, "nanos", System.nanoTime() - start));
                        }
                        case "START" -> {
                            if (jdbc.queryForObject("SELECT COUNT(*) FROM p602_process_execution WHERE task_id=?", Long.class, task) > 0) {
                                reply(Map.of("status", "DUPLICATE")); break;
                            }
                            var row = assignments.selectOne(new LambdaQueryWrapper<OnlineAssignmentEntity>().eq(OnlineAssignmentEntity::getTaskId, task));
                            var permit = authority.claim(experiment, task, row.getBindingHash());
                            authority.begin(experiment, task, permit.generation(), permit.permitHash());
                            int created = jdbc.update("INSERT IGNORE INTO p602_process_execution(task_id,experiment_id,variant,pid) VALUES(?,?,?,?)",
                                    task, experiment, row.getVariant(), ProcessHandle.current().pid());
                            if (created == 0) { reply(Map.of("status", "DUPLICATE")); break; }
                            var release = new CountDownLatch(1); var started = new CountDownLatch(1); var done = new CountDownLatch(1);
                            active.put(task, release); finished.put(task, done);
                            Thread.ofPlatform().start(() -> {
                                jdbc.update("UPDATE p602_process_execution SET started_at=NOW(3) WHERE task_id=?", task);
                                started.countDown();
                                try { release.await(); }
                                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                                finally {
                                    jdbc.update("UPDATE p602_process_execution SET finished_at=NOW(3) WHERE task_id=?", task);
                                    active.remove(task); done.countDown();
                                }
                            });
                            started.await(); reply(Map.of("status", "STARTED", "active", active.size(), "generation", permit.generation()));
                        }
                        case "FINISH" -> {
                            active.get(task).countDown(); finished.get(task).await(); reply(Map.of("status", "FINISHED", "active", active.size()));
                        }
                        case "STATUS" -> reply(Map.of("active", active.size()));
                        default -> throw new IllegalArgumentException("未知测试命令");
                    }
                } catch (BusinessException rejected) { reply(Map.of("status", "REJECTED", "reason", rejected.getMessage())); }
            }
        }
    }
    private static void reply(Map<String, ?> value) { System.out.println("P602_FIXTURE " + JsonUtils.toJson(value)); System.out.flush(); }
    private OnlineExecutionProcessFixture() { }
}
