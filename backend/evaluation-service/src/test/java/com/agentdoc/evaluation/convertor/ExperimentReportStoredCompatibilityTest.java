package com.agentdoc.evaluation.convertor;

import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.RedisUtils;
import com.agentdoc.evaluation.pojo.entity.ExperimentReportEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 显式开启才读取真实存量；事务只读，仅打印计数，不落盘原始报告或业务身份。 */
@EnabledIfEnvironmentVariable(named = "VERIFY_STORED_REPORTS", matches = "true")
class ExperimentReportStoredCompatibilityTest {
    @Test
    void existingV1ReportsKeepJsonFieldShapeAndContentHash() throws Exception {
        var convertor = new ExperimentReportConvertor(mock(RedisUtils.class));
        String url = value("DB_URL", "jdbc:mysql://localhost:3306/agent_doc_workbench?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai");
        String password = value("DB_PASSWORD", System.getenv("MYSQL_PWD"));
        if (password == null || password.isBlank()) { throw new IllegalStateException("只读存量验证需要环境变量数据库凭证"); }
        try (var connection = DriverManager.getConnection(url, value("DB_USERNAME", "root"), password)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            int count = 0;
            try (var query = connection.prepareStatement("SELECT id, experiment_id, revision, report_schema_version, selected_record_ids_json, report_json, content_hash FROM experiment_report WHERE report_schema_version = 1")) {
                query.setQueryTimeout(5);
                try (var rows = query.executeQuery()) {
                    while (rows.next()) {
                        ExperimentReportEntity entity = new ExperimentReportEntity();
                        entity.setId(rows.getLong("id"));
                        entity.setExperimentId(rows.getLong("experiment_id"));
                        entity.setRevision(rows.getInt("revision"));
                        entity.setReportSchemaVersion(rows.getInt("report_schema_version"));
                        entity.setSelectedRecordIdsJson(rows.getString("selected_record_ids_json"));
                        entity.setReportJson(rows.getString("report_json"));
                        entity.setContentHash(rows.getString("content_hash"));
                        var result = convertor.toVO(entity);
                        // 失败不把 JSON 原文或业务 ID 写入测试报告。
                        assertThat(result.compatible()).as("存量 v1 兼容").isTrue();
                        assertThat(sameJson(entity.getReportJson(), JsonUtils.toJson(result.report())))
                                .as("正文 shape 与原值不变").isTrue();
                        assertThat(sameJson(entity.getSelectedRecordIdsJson(), JsonUtils.toJson(result.selectedRecordIds())))
                                .as("选中 ID shape 不变").isTrue();
                        assertThat(result.contentHash().equals(entity.getContentHash())).isTrue();
                        count++;
                    }
                }
            } finally {
                connection.rollback();
            }
            assertThat(count).as("不能用空库冒充历史验证").isPositive();
            System.out.printf("存量 v1 报告只读兼容验证：%d 份通过%n", count);
        }
    }

    private boolean sameJson(String left, String right) {
        JsonNode original = JsonUtils.parse(left, JsonNode.class);
        return original != null && original.equals(JsonUtils.parse(right, JsonNode.class));
    }

    private static String value(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
