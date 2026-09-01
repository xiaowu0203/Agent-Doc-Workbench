package com.agentdoc.document.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.document.constant.DocumentAssetConstant;
import com.agentdoc.document.enums.DocStatus;
import com.agentdoc.document.mapper.DocumentAssetMapper;
import com.agentdoc.document.mapper.DocumentMapper;
import com.agentdoc.document.pojo.entity.DocumentAssetEntity;
import com.agentdoc.document.pojo.entity.DocumentEntity;
import com.agentdoc.document.pojo.vo.DocumentAssetVO;
import com.agentdoc.document.storage.DocumentAssetStorage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;

import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_EDIT;
import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_READ;

/**
 * 文档图片上传与受控读取服务。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAssetService {

    private final DocumentMapper documentMapper;
    private final DocumentAssetMapper documentAssetMapper;
    private final SpacePermissionService permissionService;
    private final DocumentAssetStorage assetStorage;

    /**
     * 上传图片到文档附件目录。
     *
     * @param documentId 文档 ID
     * @param file 图片文件
     * @return 附件元数据及受控访问地址
     */
    public DocumentAssetVO uploadImage(Long documentId, MultipartFile file) {
        DocumentEntity document = requireDocument(documentId);
        permissionService.requirePermission(document.getSpaceId(), DOCUMENT_EDIT);
        validateImage(file);

        Path tempFile = null;
        String objectKey = "documents/" + document.getSpaceId() + "/" + documentId
                + "/images/" + UUID.randomUUID() + "." + extension(file.getContentType());
        try {
            tempFile = Files.createTempFile("agent-doc-image-", ".upload");
            file.transferTo(tempFile);
            assetStorage.put(objectKey, tempFile, file.getContentType());

            DocumentAssetEntity asset = new DocumentAssetEntity();
            asset.setDocumentId(documentId);
            asset.setSpaceId(document.getSpaceId());
            asset.setObjectKey(objectKey);
            asset.setOriginalName(originalName(file.getOriginalFilename()));
            asset.setContentType(file.getContentType());
            asset.setSizeBytes(file.getSize());
            asset.setCreatedBy(permissionService.requireUserId());
            documentAssetMapper.insert(asset);
            return toVO(asset);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片读取失败");
        } catch (RuntimeException exception) {
            // 数据库写入失败时删除已经上传的对象，避免留下孤儿文件。
            try {
                assetStorage.delete(objectKey);
            } catch (RuntimeException cleanupException) {
                log.warn("文档图片对象清理失败，objectKey={}", objectKey, cleanupException);
            }
            throw exception;
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupException) {
                    // 临时文件清理失败不影响已完成的业务结果，但保留可追踪日志。
                    log.warn("文档图片临时文件清理失败，path={}", tempFile, cleanupException);
                }
            }
        }
    }

    /**
     * 按文档读取图片，权限由文档所属空间控制。
     */
    public ResponseEntity<Resource> readImage(Long documentId, Long assetId) {
        DocumentEntity document = requireDocument(documentId);
        permissionService.requirePermission(document.getSpaceId(), DOCUMENT_READ);
        DocumentAssetEntity asset = documentAssetMapper.selectOne(new LambdaQueryWrapper<DocumentAssetEntity>()
                .eq(DocumentAssetEntity::getId, assetId)
                .eq(DocumentAssetEntity::getDocumentId, documentId));
        if (asset == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片附件不存在");
        }
        InputStream inputStream = assetStorage.get(asset.getObjectKey());
        MediaType mediaType = MediaType.parseMediaType(asset.getContentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(asset.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new InputStreamResource(inputStream));
    }

    private DocumentEntity requireDocument(Long documentId) {
        DocumentEntity document = documentMapper.selectById(documentId);
        if (document == null || !(DocStatus.NORMAL.getCode() == document.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return document;
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片不能为空");
        }
        if (file.getSize() > DocumentAssetConstant.MAX_IMAGE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片大小不能超过 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !DocumentAssetConstant.IMAGE_EXTENSIONS.containsKey(contentType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 PNG、JPEG、GIF、WebP 图片");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(DocumentAssetConstant.IMAGE_HEADER_LENGTH);
            if (!matchesSignature(contentType, header)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "图片内容与 MIME 类型不匹配");
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片读取失败");
        }
    }

    private boolean matchesSignature(String contentType, byte[] header) {
        return switch (contentType) {
            case "image/png" -> startsWith(header, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
            case "image/jpeg" -> header.length >= 3 && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
            case "image/gif" -> startsWith(header, "GIF8".getBytes());
            case "image/webp" -> header.length >= 12 && startsWith(header, "RIFF".getBytes())
                    && startsWith(Arrays.copyOfRange(header, 8, 12), "WEBP".getBytes());
            default -> false;
        };
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private String extension(String contentType) {
        return DocumentAssetConstant.IMAGE_EXTENSIONS.get(contentType);
    }

    private String originalName(String name) {
        if (name == null || name.isBlank()) {
            return "image";
        }
        String safeName = Paths.get(name).getFileName().toString();
        return safeName.length() > 255 ? safeName.substring(0, 255) : safeName;
    }

    private DocumentAssetVO toVO(DocumentAssetEntity asset) {
        return new DocumentAssetVO(asset.getId(), asset.getDocumentId(), asset.getOriginalName(),
                asset.getContentType(), asset.getSizeBytes(),
                "/api/document/documents/" + asset.getDocumentId() + "/assets/" + asset.getId(),
                asset.getCreatedAt());
    }
}
