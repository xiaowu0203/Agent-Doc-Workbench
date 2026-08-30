package com.agentdoc.common.minio.config;

import com.agentdoc.common.minio.service.MinioObjectStorageService;
import com.agentdoc.common.minio.service.ObjectStorageService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MinIO 对象存储自动配置类
 * <p>
 * 项目引入 minio 依赖时自动激活该配置，读取 minio 配置属性，自动创建 MinioClient 客户端
 * 同时装配对象存储业务服务 Bean，支持自动创建 Bucket、Bucket 存在性校验
 * </p>
 */
@AutoConfiguration
// classpath下存在 MinioClient 类才实例化当前自动配置，没有引入minio依赖直接跳过
@ConditionalOnClass(MinioClient.class)
// 开启 MinioProperties 配置属性绑定，将配置文件中 minio.* 映射到 MinioProperties
@EnableConfigurationProperties(MinioProperties.class)
public class MinioAutoConfiguration {

    /**
     * 构建 MinIO 客户端 Bean
     * <p>
     * 条件：容器中不存在 MinioClient Bean 时才创建，允许业务自定义覆盖
     * 会校验 accessKey / secretKey 必填；同时校验并初始化 Bucket
     * </p>
     *
     * @param properties minio配置属性对象
     * @return MinioClient minio客户端实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MinioClient minioClient(MinioProperties properties) {
        // 校验必填凭证，accessKey、secretKey不能为空
        if (StringUtils.isBlank(properties.getAccessKey())
            || StringUtils.isBlank(properties.getSecretKey())) {
            throw new IllegalStateException("MinIO 凭证未配置，请设置 MINIO_ACCESS_KEY 和 MINIO_SECRET_KEY");
        }
        // 构建MinIO客户端实例，传入服务端点、账号密钥凭证
        MinioClient client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
        // 确保Bucket可用，不存在时根据配置选择自动创建或者抛出异常
        ensureBucket(client, properties);
        return client;
    }

    /**
     * 对象存储业务服务 Bean
     * <p>
     * 封装MinIO原始客户端，对外提供统一的对象存储接口 ObjectStorageService
     * 容器不存在该Bean才创建，支持业务侧自定义实现覆盖
     * </p>
     *
     * @param client     minio原生客户端
     * @param properties minio配置属性
     * @return ObjectStorageService 对象存储服务实现
     */
    @Bean
    @ConditionalOnMissingBean(ObjectStorageService.class)
    public ObjectStorageService objectStorageService(MinioClient client, MinioProperties properties) {
        return new MinioObjectStorageService(client, properties);
    }

    /**
     * 校验并保证Bucket可用
     * <ol>
     *     <li>判断Bucket是否存在</li>
     *     <li>不存在且开启自动创建，则新建Bucket</li>
     *     <li>不存在且关闭自动创建，直接抛出异常阻止项目启动</li>
     *     <li>捕获所有minio操作异常，包装为运行时异常，启动阶段直接报错</li>
     * </ol>
     *
     * @param client     minio客户端
     * @param properties minio配置，包含bucket名称、autoCreateBucket开关
     */
    private void ensureBucket(MinioClient client, MinioProperties properties) {
        try {
            // 判断Bucket是否已经存在
            boolean exists = client.bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.getBucket()).build());
            if (!exists && properties.isAutoCreateBucket()) {
                // Bucket不存在，且开启自动创建，则新建bucket
                client.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
            } else if (!exists) {
                // Bucket不存在，且关闭自动创建，启动失败抛出异常
                throw new IllegalStateException("MinIO Bucket 不存在: " + properties.getBucket());
            }
        } catch (Exception exception) {
            // minio IO/网络/权限异常包装，项目启动直接终止
            throw new IllegalStateException("MinIO Bucket 初始化失败", exception);
        }
    }
}
