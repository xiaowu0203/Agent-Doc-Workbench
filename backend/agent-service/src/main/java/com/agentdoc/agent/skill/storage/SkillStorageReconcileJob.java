package com.agentdoc.agent.skill.storage;

import com.agentdoc.agent.constant.SkillConstant;
import com.agentdoc.agent.mapper.SkillVersionMapper;
import com.agentdoc.common.minio.service.ObjectStorageService;
import com.agentdoc.common.logging.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.Duration;
import java.util.Set;
import java.util.HashSet;

/**
 * 清理对象写入成功、数据库落库失败后遗留的 Skill ZIP。
 * <p>
 * 业务场景：上传Skill包到对象存储成功，但后续数据库 SkillVersion 版本记录插入失败，
 * 对象存储就会残留没有任何DB引用的孤儿ZIP文件，占用存储空间。
 * 定时任务扫描对象存储skill‑package前缀下全部对象，对比数据库内有效storageKey集合，
 * 删除超过指定时长、数据库没有任何版本引用的孤儿文件。
 * <p>
 * 保护逻辑：
 * 1. 只处理创建时间超过 {@link #ORPHAN_AGE_HOURS} 的对象，避免删除刚上传还未完成DB落库的文件
 * 2. 数据库存在引用的storageKey绝对不会删除
 * 3. 删除失败仅打warn日志，不中断整体任务，下一轮调度继续尝试
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillStorageReconcileJob {

    /** 孤儿文件最小存活时长：超过24小时才允许被清理，给DB落库留出窗口期 */
    private static final long ORPHAN_AGE_HOURS = 24;
    private final ObjectStorageService storage;
    private final SkillVersionMapper versionMapper;

    /**
     * 定时执行存储对账清理任务
     * 调度间隔默认：21600000ms = 6小时，可通过配置 agent‑doc.skill.reconcile‑delay‑ms 覆盖
     */
    @Scheduled(fixedDelayString = "${agent-doc.skill.reconcile-delay-ms:21600000}")
    public void reconcile() {
        // 计算时间截断点：24小时之前，晚于该时间点的文件不参与清理
        Instant cutoff = Instant.now().minus(Duration.ofHours(ORPHAN_AGE_HOURS));
        // 从数据库全量拉取所有正在被SkillVersion引用的storageKey集合
        Set<String> referencedKeys = referencedStorageKeys();
        // 扫描对象存储中skill‑package前缀下全部对象，逐个执行对账判断
        storage.scan(SkillConstant.STORAGE_PREFIX, object -> reconcileObject(object, cutoff, referencedKeys));
    }

    /**
     * 一次查询数据库，收集所有SkillVersion版本实体使用过的storageKey
     *
     * @return 数据库中有效的存储key集合；null/blank的key会被过滤掉
     */
    private Set<String> referencedStorageKeys() {
        return new HashSet<>(versionMapper.selectReferencedStorageKeys());
    }

    /**
     * 对单个对象存储文件做对账判断，满足孤儿条件则执行删除
     *
     * @param object        对象存储元信息（key、最后修改时间）
     * @param cutoff        清理时间阈值，晚于此时间不处理
     * @param referencedKeys 数据库引用的合法storageKey集合
     */
    private void reconcileObject(ObjectStorageService.StorageObject object, Instant cutoff,
                                 Set<String> referencedKeys) {
        // 1.无修改时间 2.文件还比较新(不足24h) 3.数据库存在引用 → 直接跳过，不删除
        if (object.lastModified() == null || object.lastModified().isAfter(cutoff)
                || referencedKeys.contains(object.key())) {
            return;
        }
        // 判定为孤儿文件，执行删除
        try {
            storage.delete(object.key());
            log.info("已删除孤儿 Skill 对象: {}", LogSanitizer.sanitizeText(object.key()));
        } catch (RuntimeException exception) {
            // 删除异常只打警告，不抛出，不打断整个扫描流程，等待下一轮定时重试
            log.warn("孤儿 Skill 对象删除失败: objectKey={}, stack={}",
                    LogSanitizer.sanitizeText(object.key()), LogSanitizer.sanitizeThrowable(exception));
        }
    }
}
