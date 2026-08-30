package com.agentdoc.common.minio.service;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * 统一对象存储服务接口
 * <p>
 * 面向业务层定义对象存储标准操作，屏蔽底层存储实现（当前实现为MinIO）。
 * 后续可快速切换其他存储实现，业务代码无需改动。
 * 所有方法发生存储异常时抛出 {@link ObjectStorageException}
 * </p>
 */
public interface ObjectStorageService {

    /**
     * 上传本地文件到默认 Bucket
     *
     * @param objectKey   对象存储key，即存储路径
     * @param source      本地待上传文件路径
     * @param contentType 文件MIME类型，可为null，实现类会提供默认二进制类型
     * @throws ObjectStorageException IO、网络、权限等存储异常
     */
    void put(String objectKey, Path source, String contentType);

    /**
     * 流式读取对象
     * <p><b>重要：调用方必须关闭返回的InputStream，否则会发生连接泄漏</b></p>
     *
     * @param objectKey 对象存储key
     * @return 对象输入流
     * @throws ObjectStorageException 对象不存在、网络、权限异常
     */
    InputStream get(String objectKey);

    /**
     * 判断对象是否存在
     *
     * @param objectKey 对象存储key
     * @return true 对象存在；false 对象/Bucket不存在
     * @throws ObjectStorageException 网络、权限等非不存在类异常
     */
    boolean exists(String objectKey);

    /**
     * 幂等删除对象
     * <p>对象不存在时不会抛出异常</p>
     *
     * @param objectKey 对象存储key
     * @throws ObjectStorageException 网络、权限异常
     */
    void delete(String objectKey);

    /**
     * 列举指定前缀下的对象及其最后修改时间
     * <p>会将全部对象加载进内存，对象数量非常大时请优先使用 {@link #scan(String, Consumer)}</p>
     *
     * @param prefix 对象key前缀；传null代表查询Bucket下全部对象
     * @return 对象元数据列表 {@link StorageObject}
     * @throws ObjectStorageException 列表查询失败
     */
    List<StorageObject> list(String prefix);

    /**
     * 流式扫描指定前缀下的对象，逐条消费，避免调用方一次性持有全部对象到内存
     * <p>接口提供默认实现：先调用list全部加载再遍历消费；实现类可重写该方法做真正流式迭代（如MinIO实现）</p>
     *
     * @param prefix   对象key前缀；传null代表查询Bucket下全部对象
     * @param consumer 单条对象元数据消费回调
     * @throws ObjectStorageException 扫描遍历过程异常
     */
    default void scan(String prefix, Consumer<StorageObject> consumer) {
        list(prefix).forEach(consumer);
    }

    /**
     * 存储对象元数据记录
     *
     * @param key          对象存储key
     * @param lastModified 对象最后修改时间，UTC时间戳；可能为null
     */
    record StorageObject(String key, Instant lastModified) {
    }
}
