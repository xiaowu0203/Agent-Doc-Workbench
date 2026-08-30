package com.agentdoc.agent.skill.storage;

import com.agentdoc.agent.constant.SkillConstant;
import com.agentdoc.common.minio.service.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * 技能包对象存储服务
 * <p>
 * 封装技能压缩包的对象存储操作，负责生成存储Key、上传、下载、判存、删除。
 * 所有技能包原始zip文件统一存放在对象存储，数据库仅保存storageKey元数据，
 * 不存储二进制包体。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SkillPackageStorage {

    private final ObjectStorageService objectStorageService;

    /**
     * 生成技能包在对象存储中的完整对象Key
     *
     * @param spaceId   空间ID
     * @param skillId   技能ID
     * @param versionNo 技能版本号
     * @param sha256    技能zip包SHA‑256摘要
     * @return 对象存储完整key，格式：skill-package/{spaceId}/{skillId}/{versionNo}/{sha256}.zip
     */
    public String key(Long spaceId, Long skillId, int versionNo, String sha256) {
        return SkillConstant.STORAGE_PREFIX + spaceId + "/" + skillId + "/" + versionNo + "/" + sha256 + ".zip";
    }

    /**
     * 上传本地zip文件到对象存储
     *
     * @param key    对象存储key
     * @param source 本地zip文件路径
     */
    public void put(String key, Path source) {
        objectStorageService.put(key, source, SkillConstant.ZIP_CONTENT_TYPE);
    }

    /**
     * 从对象存储读取技能zip包输入流
     * <p>调用方需要负责关闭返回的InputStream。</p>
     *
     * @param key 对象存储key
     * @return zip文件输入流
     */
    public InputStream get(String key) {
        return objectStorageService.get(key);
    }

    /**
     * 判断指定key的技能包是否在对象存储中存在
     *
     * @param key 对象存储key
     * @return true已存在；false不存在
     */
    public boolean exists(String key) {
        return objectStorageService.exists(key);
    }

    /**
     * 删除对象存储中的技能zip包
     *
     * @param key 对象存储key
     */
    public void delete(String key) {
        objectStorageService.delete(key);
    }
}
