package com.agentdoc.document.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.document.pojo.vo.DocumentAssetVO;
import com.agentdoc.document.service.DocumentAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档图片附件接口。
 */
@Tag(name = "文档图片", description = "文档图片上传和受控读取")
@RestController
@RequestMapping("/api/document/documents/{documentId}/assets")
@RequireLogin
@RequiredArgsConstructor
public class DocumentAssetController {

    private final DocumentAssetService documentAssetService;

    @Operation(summary = "上传文档图片")
    @PostMapping(value = "/images", consumes = "multipart/form-data")
    public Result<DocumentAssetVO> uploadImage(@PathVariable Long documentId,
                                               @RequestPart("file") MultipartFile file) {
        return Result.ok(documentAssetService.uploadImage(documentId, file));
    }

    @Operation(summary = "读取文档图片")
    @GetMapping("/{assetId}")
    public ResponseEntity<Resource> readImage(@PathVariable Long documentId, @PathVariable Long assetId) {
        return documentAssetService.readImage(documentId, assetId);
    }
}
