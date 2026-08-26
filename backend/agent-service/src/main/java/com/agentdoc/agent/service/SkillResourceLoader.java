package com.agentdoc.agent.service;

import com.agentdoc.agent.constant.SkillConstant;
import com.agentdoc.agent.execution.runtime.SkillExecutionSnapshot;
import com.agentdoc.common.minio.service.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 按执行快照从 MinIO 加载 reference/example 文本资源。
 * <p>
 * 在Skill Agent执行阶段，根据执行快照里绑定的技能版本快照，从对象存储读取Skill ZIP包，
 * 只加载快照标记为runtimeReadable的资源(references、examples)，做哈希完整性校验、大小限制、UTF‑8校验，
 * 返回内存中解码完成的文本资源集合，供Agent注入上下文使用。
 * <p>
 * 安全保障：
 * 1. 只加载快照预先记录的可读资源，不会读取zip中任意文件
 * 2. 校验单个资源文件哈希，防止包被篡改
 * 3. 校验整个ZIP归档包哈希，校验整个技能包完整性
 * 4. 带上限读取，防止超大文件内存溢出
 * 5. 强制严格UTF‑8解码，拒绝损坏文本
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SkillResourceLoader {

    private final ObjectStorageService storage;

    /**
     * 根据技能执行快照，批量加载快照中所有绑定技能的可读资源
     *
     * @param snapshot 技能执行快照，保存本次执行绑定的技能版本、可读资源清单、storageKey、sha256等快照信息
     * @return 加载完成的资源集合，key:skillVersionId，value:path→LoadedResource
     * @throws IllegalStateException IO异常、哈希校验失败、大小超限、非UTF‑8文本均抛出该运行时异常
     */
    public LoadedSkillResources load(SkillExecutionSnapshot snapshot) {
        // key: skillVersionId, value: <资源相对路径, 加载后资源对象>
        Map<Long, Map<String, LoadedResource>> resources = new HashMap<>();

        // 遍历快照中本次执行绑定的每一个技能版本快照
        for (SkillExecutionSnapshot.BoundSkillSnapshot skill : snapshot.skills()) {
            // 将快照记录的可读资源列表转为Map，key=包内相对路径，快速匹配zip内条目
            Map<String, SkillPackageEntry> expected = skill.readableResources().stream()
                    .collect(Collectors.toMap(SkillPackageEntry::path, value -> value));

            // 当前技能版本无可读资源，直接跳过
            if (expected.isEmpty()) {
                continue;
            }

            Map<String, LoadedResource> loaded = new HashMap<>();
            try (
                 // 从对象存储获取技能ZIP包原始流
                 InputStream source = storage.get(skill.storageKey());
                 // 对整个ZIP归档计算SHA‑256，用于校验整个包未被篡改
                 DigestInputStream digestInput = new DigestInputStream(source,
                         MessageDigest.getInstance(SkillConstant.SHA_256));
                 // zip解压流，使用UTF‑8解析zip条目名称
                 ZipArchiveInputStream zip = new ZipArchiveInputStream(digestInput, StandardCharsets.UTF_8.name(), false))
            {
                ZipArchiveEntry entry;
                // 遍历zip包内所有条目
                while ((entry = zip.getNextZipEntry()) != null) {
                    // 跳过目录，只处理文件
                    if (entry.isDirectory()) {
                        continue;
                    }
                    // 把zip原始entry路径转为包内相对路径（剥离顶层skill目录）
                    String path = entry.getName().replace('\\', '/');
                    int slash = path.indexOf('/');
                    String relative = slash < 0 ? path : path.substring(slash + 1);

                    // 只处理快照中预先声明的可读资源，其他文件直接跳过（scripts/assets不会被读取）
                    SkillPackageEntry expectedEntry = expected.get(relative);
                    if (expectedEntry == null) {
                        continue;
                    }

                    // 按快照记录的文件大小上限安全读取文件字节
                    byte[] bytes = readBounded(zip, expectedEntry.size());
                    // 校验当前文件内容SHA‑256，和快照记录比对，检测单文件篡改
                    String actualHash = HexFormat.of().formatHex(
                            MessageDigest.getInstance(SkillConstant.SHA_256).digest(bytes));
                    if (!actualHash.equals(expectedEntry.sha256())) {
                        throw new IllegalStateException("Skill 资源哈希不匹配: " + relative);
                    }
                    // UTF‑8解码文本，封装为内存资源对象
                    loaded.put(relative, new LoadedResource(relative, expectedEntry.type().name(), expectedEntry.size(),
                            decodeUtf8(bytes, relative)));
                }

                // 校验整个ZIP归档包整体SHA‑256，和快照记录比对，校验整个技能包完整性
                String actualPackageHash = HexFormat.of().formatHex(digestInput.getMessageDigest().digest());
                if (!actualPackageHash.equals(skill.sha256())) {
                    throw new IllegalStateException("Skill 包哈希不匹配: " + skill.name());
                }
            } catch (Exception exception) {
                // 原样抛出运行时异常；IO等受检异常包装为IllegalStateException
                if (exception instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("Skill 资源加载失败: " + skill.name(), exception);
            }
            resources.put(skill.skillVersionId(), Map.copyOf(loaded));
        }

        // 放入结果集合，不可修改Map
        return new LoadedSkillResources(Map.copyOf(resources));
    }

    private byte[] readBounded(InputStream input, long max) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(max, 8192));
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > max) {
                throw new IllegalStateException("Skill 资源大小超过快照限制");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    /**
     * 带最大字节上限从输入流读取字节，防止读取超大文件OOM
     *
     * @param input zip条目输入流
     * @param max   最大允许字节数（取自快照中记录的size）
     * @return 文件完整字节数组
     * @throws IOException IO异常；超过max上限抛出IllegalStateException
     */
    private String decodeUtf8(byte[] bytes, String path) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Skill 资源不是合法 UTF-8: " + path, exception);
        }
    }

    /**
     * 加载完成的技能可读资源集合
     *
     * @param resourcesByVersionId key:技能版本ID；value: Map<资源相对路径, LoadedResource>
     */
    public record LoadedSkillResources(Map<Long, Map<String, LoadedResource>> resourcesByVersionId) {
    }

    /**
     * 单个加载完毕的可读文本资源
     *
     * @param path  包内相对路径
     * @param type  资源类型枚举名称，对应SkillEntryType
     * @param size  文件字节大小
     * @param content UTF‑8解码完成的文本内容
     */
    public record LoadedResource(String path, String type, long size, String content) {
    }
}
