package com.agentdoc.common.minio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** MinIO 连接与 Bucket 配置。 */
@Data
@ConfigurationProperties(prefix = "agent-doc.minio")
public class MinioProperties {

    /** MinIO API 地址。 */
    private String endpoint = "http://localhost:9000";
    /** MinIO Access Key。 */
    private String accessKey;
    /** MinIO Secret Key。 */
    private String secretKey;
    /** 默认 Bucket。 */
    private String bucket = "agent-doc";
    /** 是否在启动时自动创建 Bucket。 */
    private boolean autoCreateBucket = true;
}
