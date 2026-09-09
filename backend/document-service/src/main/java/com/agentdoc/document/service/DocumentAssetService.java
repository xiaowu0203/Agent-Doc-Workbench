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
 * 文档附件资产服务
 * 负责文档内图片附件的上传、存储、读取；做文件类型校验、魔数校验、权限控制、孤儿文件清理
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
     * 上传图片附件到文档资产存储
     * 存储路径：documents/{spaceId}/{documentId}/images/随机UUID.后缀
     * 权限：需要文档编辑权限；上传失败会清理已经存入存储的对象，避免孤儿文件
     *
     * @param documentId 所属文档ID
     * @param file 上传的图片文件
     * @return 附件元数据VO，包含访问相对地址
     */
    public DocumentAssetVO uploadImage(Long documentId, MultipartFile file) {
        // 校验文档存在且状态正常
        DocumentEntity document = requireDocument(documentId);
        // 校验当前用户拥有该空间文档编辑权限
        permissionService.requirePermission(document.getSpaceId(), DOCUMENT_EDIT);
        // 校验图片文件格式、大小、魔数
        validateImage(file);

        Path tempFile = null;
        // 生成对象存储key，使用UUID避免文件名冲突
        String objectKey = "documents/" + document.getSpaceId() + "/" + documentId
                + "/images/" + UUID.randomUUID() + "." + extension(file.getContentType());
        try {
            // 创建本地临时文件，接收上传文件
            tempFile = Files.createTempFile("agent-doc-image-", ".upload");
            file.transferTo(tempFile);
            // 将临时文件写入对象存储
            assetStorage.put(objectKey, tempFile, file.getContentType());

            // 构建附件数据库实体
            DocumentAssetEntity asset = new DocumentAssetEntity();
            asset.setDocumentId(documentId);
            asset.setSpaceId(document.getSpaceId());
            asset.setObjectKey(objectKey);
            asset.setOriginalName(originalName(file.getOriginalFilename()));
            asset.setContentType(file.getContentType());
            asset.setSizeBytes(file.getSize());
            asset.setCreatedBy(permissionService.requireUserId());
            // 插入附件记录
            documentAssetMapper.insert(asset);
            return toVO(asset);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片读取失败");
        } catch (RuntimeException exception) {
            // 数据库写入异常，需要清理对象存储中已经上传成功的文件，防止遗留孤儿文件
            try {
                assetStorage.delete(objectKey);
            } catch (RuntimeException cleanupException) {
                log.warn("文档图片对象清理失败，objectKey={}", objectKey, cleanupException);
            }
            throw exception;
        } finally {
            // 无论成功失败，删除本地临时文件；清理失败只打警告日志
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
     * 根据文档ID、附件ID读取图片附件
     * 权限：校验文档空间的文档读取权限；校验附件归属对应文档，防止越权访问其他文档图片
     *
     * @param documentId 文档ID
     * @param assetId 附件ID
     * @return 返回图片资源响应，inline内联展示
     */
    public ResponseEntity<Resource> readImage(Long documentId, Long assetId) {
        // 校验文档存在且状态正常
        DocumentEntity document = requireDocument(documentId);
        // 校验文档读取权限
        permissionService.requirePermission(document.getSpaceId(), DOCUMENT_READ);

        // 查询附件，必须同时匹配assetId和documentId，防止跨文档越权读取
        DocumentAssetEntity asset = documentAssetMapper.selectOne(new LambdaQueryWrapper<DocumentAssetEntity>()
                .eq(DocumentAssetEntity::getId, assetId)
                .eq(DocumentAssetEntity::getDocumentId, documentId));
        if (asset == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片附件不存在");
        }

        // 从对象存储读取文件流
        InputStream inputStream = assetStorage.get(asset.getObjectKey());
        MediaType mediaType = MediaType.parseMediaType(asset.getContentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(asset.getSizeBytes())
                // 浏览器内联预览，不触发下载
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new InputStreamResource(inputStream));
    }

    /**
     * 校验文档是否存在并且状态为正常
     * @param documentId 文档ID
     * @return 文档实体
     */
    private DocumentEntity requireDocument(Long documentId) {
        DocumentEntity document = documentMapper.selectById(documentId);
        if (document == null || !(DocStatus.NORMAL.getCode() == document.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return document;
    }

    /**
     * 图片文件校验
     * 校验：文件非空、大小上限、MIME类型、文件魔数（头部字节），防止上传伪装后缀的恶意文件
     * @param file 上传文件
     */
    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片不能为空");
        }

        // 校验图片大小上限
        if (file.getSize() > DocumentAssetConstant.MAX_IMAGE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片大小不能超过 10MB");
        }
        // 校验MIME类型是否在允许列表
        String contentType = file.getContentType();
        if (contentType == null || !DocumentAssetConstant.IMAGE_EXTENSIONS.containsKey(contentType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 PNG、JPEG、GIF、WebP 图片");
        }
        // 读取文件头部字节，校验魔数，防止MIME被篡改
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(DocumentAssetConstant.IMAGE_HEADER_LENGTH);
            if (!matchesSignature(contentType, header)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "图片内容与 MIME 类型不匹配");
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片读取失败");
        }
    }

    /**
     * 根据MIME类型校验文件头部魔数签名
     * @param contentType 文件MIME类型
     * @param header 文件头部字节数组
     * @return true魔数匹配，false不匹配
     */
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

    /**
     * 判断字节数组是否以指定前缀字节开头
     * @param value 待校验字节数组
     * @param prefix 前缀字节
     * @return true匹配前缀
     */
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

    /**
     * 根据MIME类型获取文件后缀
     * @param contentType MIME类型
     * @return 文件后缀
     */
    private String extension(String contentType) {
        return DocumentAssetConstant.IMAGE_EXTENSIONS.get(contentType);
    }

    /**
     * 处理原始文件名，获取安全文件名，限制最大长度255字符
     * @param name 原始文件名
     * @return 安全处理后的文件名
     */
    private String originalName(String name) {
        if (name == null || name.isBlank()) {
            return "image";
        }
        String safeName = Paths.get(name).getFileName().toString();
        return safeName.length() > 255 ? safeName.substring(0, 255) : safeName;
    }

    /**
     * 附件实体转VO
     * @param asset 附件数据库实体
     * @return 附件VO，包含前端访问url路径
     */
    private DocumentAssetVO toVO(DocumentAssetEntity asset) {
        return new DocumentAssetVO(asset.getId(), asset.getDocumentId(), asset.getOriginalName(),
                asset.getContentType(), asset.getSizeBytes(),
                "/api/document/documents/" + asset.getDocumentId() + "/assets/" + asset.getId(),
                asset.getCreatedAt());
    }
}
