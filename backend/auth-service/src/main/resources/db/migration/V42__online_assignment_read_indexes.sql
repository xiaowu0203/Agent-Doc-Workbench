-- 百万分配规模：实验参与文档汇总避免回表，分页按实验/空间/schema/id 定位。
ALTER TABLE online_assignment
    ADD KEY idx_online_assignment_summary (experiment_id, binding_schema_version, document_id),
    ADD KEY idx_online_assignment_page (experiment_id, space_id, binding_schema_version, id);
