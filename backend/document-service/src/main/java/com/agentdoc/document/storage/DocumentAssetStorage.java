package com.agentdoc.document.storage;

import com.agentdoc.common.minio.service.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * 文档附件存储适配层，隔离文档领域与 MinIO 实现细节。
 */
@Component
@RequiredArgsConstructor
public class DocumentAssetStorage {

    private final ObjectStorageService objectStorageService;

    /**
     * 文档资产对象存储写入
     * 将本地文件路径的资源存入对象存储
     * @param objectKey 对象存储唯一key
     * @param source 本地文件路径
     * @param contentType 文件MIME类型
     */
    public void put(String objectKey, Path source, String contentType) {
        objectStorageService.put(objectKey, source, contentType);
    }

    /**
     * 根据objectKey读取对象存储资源
     * @param objectKey 对象存储唯一key
     * @return 文件输入流，调用方需要自行关闭流
     */
    public InputStream get(String objectKey) {
        return objectStorageService.get(objectKey);
    }

    /**
     * 删除对象存储中的文档资产
     * @param objectKey 对象存储唯一key
     */
    public void delete(String objectKey) {
        objectStorageService.delete(objectKey);
    }
}
