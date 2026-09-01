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

    public void put(String objectKey, Path source, String contentType) {
        objectStorageService.put(objectKey, source, contentType);
    }

    public InputStream get(String objectKey) {
        return objectStorageService.get(objectKey);
    }

    public void delete(String objectKey) {
        objectStorageService.delete(objectKey);
    }
}
