package com.agentdoc.common.minio.service;

import com.agentdoc.common.minio.config.MinioProperties;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * MinIO 对象存储服务实现类
 * 实现统一对象存储接口 {@link ObjectStorageService}，封装 MinIO 原生客户端操作，
 * 统一异常包装，屏蔽底层 MinIO SDK 细节，向上层业务提供存储能力
 */
@RequiredArgsConstructor
public class MinioObjectStorageService implements ObjectStorageService {

    /**
     * 默认文件MIME类型，未传入content‑type时使用二进制流类型
     */
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    /**
     * MinIO 资源不存在的错误码集合
     * 匹配对象不存在、Bucket不存在、资源未找到等场景，用于exists方法做判空逻辑
     */
    private static final Set<String> NOT_FOUND_ERROR_CODES = Set.of(
            "NoSuchKey", "NoSuchObject", "NoSuchBucket", "NotFound");

    private final MinioClient client;
    private final MinioProperties properties;

    /**
     * 上传本地文件到对象存储
     *
     * @param objectKey  对象存储中的对象key（文件路径）
     * @param source     本地文件路径
     * @param contentType 文件MIME类型，传null时使用默认二进制类型
     * @throws ObjectStorageException 上传发生IO、网络、权限等异常时抛出
     */
    @Override
    public void put(String objectKey, Path source, String contentType) {
        // try‑with‑resource 自动关闭文件输入流
        try (InputStream input = Files.newInputStream(source)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .stream(input, Files.size(source), -1)
                    .contentType(contentType == null ? DEFAULT_CONTENT_TYPE : contentType)
                    .build());
        } catch (Exception exception) {
            throw new ObjectStorageException("对象上传失败", exception);
        }
    }

    /**
     * 获取对象输入流
     * <p>
     * ⚠️注意：调用方使用完成后必须关闭返回的InputStream，否则会造成连接泄漏
     * </p>
     *
     * @param objectKey 对象key
     * @return 对象输入流
     * @throws ObjectStorageException 对象不存在、网络异常、权限异常抛出
     */
    @Override
    public InputStream get(String objectKey) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new ObjectStorageException("对象读取失败", exception);
        }
    }

    /**
     * 判断对象是否存在
     *
     * @param objectKey 对象key
     * @return true‑对象存在；false‑对象/Bucket不存在
     * @throws ObjectStorageException 非不存在类错误（网络、权限）抛出该异常
     */
    @Override
    public boolean exists(String objectKey) {
        try {
            // 获取对象元信息，无异常代表对象存在
            client.statObject(StatObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
            return true;
        } catch (ErrorResponseException exception) {
            // MinIO返回业务错误响应，取出错误码
            String errorCode = exception.errorResponse() == null ? null : exception.errorResponse().code();
            // 属于资源不存在错误码，返回false，不抛异常
            if (NOT_FOUND_ERROR_CODES.contains(errorCode)) {
                return false;
            }
            // 其他错误向上抛出
            throw new ObjectStorageException("对象状态查询失败", exception);
        } catch (Exception exception) {
            throw new ObjectStorageException("对象状态查询失败", exception);
        }
    }

    /**
     * 删除指定对象
     * <p>对象不存在时MinIO不会报错，属于幂等操作</p>
     *
     * @param objectKey 对象key
     * @throws ObjectStorageException 网络、权限异常抛出
     */
    @Override
    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new ObjectStorageException("对象删除失败", exception);
        }
    }

    /**
     * 列出指定前缀下全部对象，返回不可修改List集合
     *
     * @param prefix 对象key前缀，null等价于查询全部
     * @return 对象元数据只读列表 {@link StorageObject}
     * @throws ObjectStorageException 列表查询异常抛出
     */
    @Override
    public List<StorageObject> list(String prefix) {
        List<StorageObject> objects = new ArrayList<>();
        // 使用scan遍历消费，填充到集合
        scan(prefix, objects::add);
        // 返回不可修改副本，防止外部修改内部集合
        return List.copyOf(objects);
    }

    /**
     * 流式扫描遍历指定前缀下所有对象（递归扫描子目录）
     * <p>适合对象数量很大场景，不用一次性全部加载内存，通过consumer逐条消费</p>
     *
     * @param prefix   对象key前缀，null等价于查询全部
     * @param consumer 每条对象元数据回调处理器
     * @throws ObjectStorageException 遍历过程出现网络、权限等异常抛出
     */
    @Override
    public void scan(String prefix, Consumer<StorageObject> consumer) {
        try {
            // recursive(true) 递归遍历所有子路径
            client.listObjects(ListObjectsArgs.builder()
                    .bucket(properties.getBucket())
                    .prefix(prefix == null ? "" : prefix)
                    .recursive(true)
                    .build()).forEach(result -> {
                try {
                    Item item = result.get();
                    Instant modified = item.lastModified() == null ? null : item.lastModified().toInstant();
                    // 将minio item转换为业务存储对象模型，交给回调消费
                    consumer.accept(new StorageObject(item.objectName(), modified));
                } catch (Exception exception) {
                    throw new ObjectStorageException("对象列表读取失败", exception);
                }
            });
        } catch (ObjectStorageException exception) {
            // 已经是自定义业务异常直接透传
            throw exception;
        } catch (Exception exception) {
            throw new ObjectStorageException("对象列表读取失败", exception);
        }
    }
}
