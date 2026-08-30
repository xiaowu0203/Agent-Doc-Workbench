package com.agentdoc.agent.skill.archive;

import com.agentdoc.agent.config.SkillPackageProperties;
import com.agentdoc.agent.constant.SkillConstant;
import com.agentdoc.agent.enums.SkillEntryType;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 标准 Skill ZIP 包校验器。只读取临时文件，不执行其中任何内容。
 * <p>
 * 完成技能压缩包完整安全校验与解析：大小限制、压缩炸弹防护、路径防穿越、符号链接拦截、
 * 顶层目录规范、SKILL.md Front‑Matter YAML解析、目录结构校验、文件扩展名白名单、UTF‑8编码校验、
 * 资源大小统计，输出结构化的 {@link ParsedSkillPackage}。
 * 所有校验失败抛出 {@link SkillPackageValidationException}。
 * </p>
 */
@Component
public class SkillPackageValidator {

    /** Skill包顶层目录名称规则：kebab‑case小写字母数字+短横线 */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    /** 允许的一级子目录集合 */
    private static final Set<String> TOP_LEVEL_DIRECTORIES = Set.of("scripts", "references", "assets", "examples");
    /** scripts目录允许的脚本文件后缀 */
    private static final Set<String> SCRIPT_EXTENSIONS = Set.of("py", "sh", "js");
    /** references / examples 可读资源允许的文本后缀 */
    private static final Set<String> READABLE_EXTENSIONS = Set.of("md", "txt", "json", "yaml", "yml", "csv");

    private final SkillPackageProperties properties;

    public SkillPackageValidator(SkillPackageProperties properties) {
        this.properties = properties;
    }

    /**
     * 对本地临时ZIP文件执行完整校验、解压解析、元信息提取
     *
     * @param zipFile 待校验的本地临时zip文件路径
     * @return 解析完成的 {@link ParsedSkillPackage} 结构化包信息
     * @throws SkillPackageValidationException 包格式、安全、大小、yaml解析等全部校验失败抛出
     * @throws IOException 文件读取IO异常会包装为SkillPackageValidationException
     */
    public ParsedSkillPackage validate(Path zipFile) {
        try {
            // 校验压缩包整体大小
            long packageSize = Files.size(zipFile);
            if (packageSize <= 0 || packageSize > properties.getMaxArchiveSize().toBytes()) {
                throw invalid("ZIP 大小超过限制");
            }

            // 计算整个zip归档文件SHA‑256
            String archiveSha256 = sha256(zipFile);
            Map<String, byte[]> fileContents = new LinkedHashMap<>();
            Set<String> normalizedPaths = new HashSet<>();
            List<EntryMeta> entries = new ArrayList<>();
            String root = null;
            long uncompressedSize = 0;

            try (InputStream input = Files.newInputStream(zipFile);
                 ZipArchiveInputStream zip = new ZipArchiveInputStream(input, StandardCharsets.UTF_8.name(), false)) {
                ZipArchiveEntry entry;
                while ((entry = zip.getNextZipEntry()) != null) {
                    String rawName = entry.getName();

                    // 跳过mac系统生成的垃圾文件
                    if (isIgnored(rawName)) {
                        continue;
                    }

                    // 路径归一化，防御路径穿越
                    String normalized = normalizePath(rawName);
                    String[] parts = normalized.split("/");
                    if (parts.length == 0 || parts[0].isBlank()) {
                        throw invalid("ZIP 存在空路径");
                    }

                    // 强制整个包只能有同一个顶层根目录
                    if (root == null) {
                        root = parts[0];
                    } else if (!root.equals(parts[0])) {
                        throw invalid("ZIP 必须只有一个顶层 Skill 目录");
                    }
                    // 禁止符号链接，防止指向包外文件
                    if (entry.isUnixSymlink()) {
                        throw invalid("ZIP 不允许符号链接: " + normalized);
                    }
                    // 目录项只做路径校验，不读取内容
                    if (entry.isDirectory()) {
                        continue;
                    }
                    // 文件不能直接放在顶层根目录下
                    if (parts.length < 2) {
                        throw invalid("ZIP 顶层目录下只能包含 Skill 文件");
                    }
                    // 去掉根目录，得到包内相对路径
                    String relative = normalized.substring(root.length() + 1);
                    // 大小写无关去重，防止同文件大小写变体冲突
                    if (!normalizedPaths.add(relative.toLowerCase(Locale.ROOT))) {
                        throw invalid("ZIP 存在重复归一化路径: " + relative);
                    }
                    // 总文件数量上限
                    if (entries.size() >= properties.getMaxFileCount()) {
                        throw invalid("ZIP 文件数量超过限制");
                    }
                    long declaredSize = entry.getSize();
                    long compressedSize = entry.getCompressedSize();
                    // 单文件解压后大小限制
                    if (declaredSize > properties.getMaxSingleFileSize().toBytes()) {
                        throw invalid("文件大小超过限制: " + relative);
                    }
                    // 压缩比炸弹防护：极高压缩比的恶意文件
                    if (compressionRatioExceeded(declaredSize, compressedSize,
                            properties.getMaxCompressionRatio())) {
                        throw invalid("文件压缩比超过限制: " + relative);
                    }
                    // 带上限安全读取文件内容
                    byte[] content = readBounded(zip, properties.getMaxSingleFileSize().toBytes(), relative);
                    uncompressedSize += content.length;
                    // 全部解压后总大小上限，防护压缩炸弹
                    if (uncompressedSize > properties.getMaxUncompressedSize().toBytes()) {
                        throw invalid("ZIP 解压后大小超过限制");
                    }
                    fileContents.put(relative, content);
                    entries.add(new EntryMeta(relative, content));
                }
            }
            if (root == null) {
                throw invalid("ZIP 为空");
            }

            // 校验顶层目录名称格式
            if (root.length() > SkillConstant.MAX_NAME_LENGTH || !NAME_PATTERN.matcher(root).matches()) {
                throw invalid("Skill 顶层目录名必须为 kebab-case");
            }

            // 必须存在 SKILL.md 元文件
            byte[] skillBytes = fileContents.get("SKILL.md");
            if (skillBytes == null) {
                throw invalid("Skill 缺少 SKILL.md");
            }
            if (skillBytes.length > properties.getMaxSkillMdSize().toBytes()) {
                throw invalid("SKILL.md 大小超过限制");
            }

            // 解析SKILL.md的YAML front‑matter
            FrontMatter frontMatter = parseFrontMatter(skillBytes);
            if (!root.equals(frontMatter.name())) {
                throw invalid("顶层目录名必须与 SKILL.md.name 一致");
            }
            // 顶层目录名必须与front‑matter内name一致
            if (frontMatter.name().length() > SkillConstant.MAX_NAME_LENGTH || !NAME_PATTERN.matcher(frontMatter.name()).matches()) {
                throw invalid("SKILL.md.name 必须为 kebab-case");
            }

            List<SkillPackageEntry> resultEntries = new ArrayList<>();
            long readableSize = 0;
            for (EntryMeta meta : entries) {
                SkillEntryType type = entryType(meta.path());
                // 校验路径归属、扩展名白名单
                validateAllowedPath(meta.path(), type);
                // references / examples 标记为运行时可读资源
                boolean readable = type == SkillEntryType.REFERENCE || type == SkillEntryType.EXAMPLE;
                if (readable) {
                    if (meta.content().length > properties.getMaxReadableResourceSize().toBytes()) {
                        throw invalid("可读资源单文件超过限制: " + meta.path());
                    }
                    // 可读资源强制校验UTF‑8
                    decodeUtf8(meta.content(), meta.path());
                    readableSize += meta.content().length;
                    if (readableSize > properties.getMaxReadableResourcesSize().toBytes()) {
                        throw invalid("可读资源累计大小超过限制");
                    }
                }
                resultEntries.add(new SkillPackageEntry(meta.path(), type, meta.content().length,
                        sha256(meta.content()), readable));
            }

            // 校验技能指令文本总字节上限
            if (frontMatter.instructionText().getBytes(StandardCharsets.UTF_8).length
                    > properties.getMaxSkillInstructionsSize().toBytes()) {
                throw invalid("Skill 指令正文超过限制");
            }
            return new ParsedSkillPackage(frontMatter.name(), frontMatter.description(), frontMatter.instructionText(),
                    frontMatter.allowedTools(), List.copyOf(resultEntries), packageSize, uncompressedSize,
                    entries.size(), readableSize, archiveSha256);
        } catch (SkillPackageValidationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new SkillPackageValidationException("Skill ZIP 读取失败", exception);
        }
    }

    /**
     * 校验文件路径与扩展名是否符合目录&后缀白名单规则
     * @param path 包内相对路径
     * @param type 解析得到的条目类型
     */
    private void validateAllowedPath(String path, SkillEntryType type) {
        if (type == null) {
            throw invalid("Skill 包包含不允许的文件路径: " + path);
        }
        if (type == SkillEntryType.SCRIPT && !SCRIPT_EXTENSIONS.contains(extension(path))) {
            throw invalid("脚本文件扩展名不允许: " + path);
        }
        if ((type == SkillEntryType.REFERENCE || type == SkillEntryType.EXAMPLE)
                && !READABLE_EXTENSIONS.contains(extension(path))) {
            throw invalid("可读资源扩展名不允许: " + path);
        }
    }

    /**
     * 根据包内相对路径判断文件所属条目类型
     * @param path 包内相对路径
     * @return SkillEntryType；不允许的目录返回null
     */
    private SkillEntryType entryType(String path) {
        if (path.equals("SKILL.md")) {
            return SkillEntryType.INSTRUCTION;
        }
        int slash = path.indexOf('/');
        if (slash < 1) {
            return null;
        }
        String directory = path.substring(0, slash);
        if (!TOP_LEVEL_DIRECTORIES.contains(directory)) {
            return null;
        }
        return switch (directory) {
            case "scripts" -> SkillEntryType.SCRIPT;
            case "references" -> SkillEntryType.REFERENCE;
            case "assets" -> SkillEntryType.ASSET;
            case "examples" -> SkillEntryType.EXAMPLE;
            default -> null;
        };
    }

    /**
     * 解析SKILL.md字节内容，提取YAML Front‑Matter与指令正文
     * @param bytes SKILL.md原始字节
     * @return 解析完成的FrontMatter记录
     */
    private FrontMatter parseFrontMatter(byte[] bytes) {
        String text = decodeUtf8(bytes, "SKILL.md").replace("\r\n", "\n").replace('\r', '\n');
        // 移除BOM标记
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        if (!text.startsWith("---\n")) {
            throw invalid("SKILL.md 缺少 YAML Front Matter");
        }
        int end = text.indexOf("\n---\n", 4);
        if (end < 0) {
            throw invalid("SKILL.md Front Matter 未闭合");
        }
        String yamlText = text.substring(4, end);
        String instruction = text.substring(end + 5);
        if (yamlText.codePointCount(0, yamlText.length()) > properties.getMaxYamlCodePoints()) {
            throw invalid("SKILL.md Front Matter 文本长度超过限制");
        }

        // SnakeYAML安全配置，防御炸弹、别名、递归、过深嵌套
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(0);
        options.setAllowRecursiveKeys(false);
        options.setAllowDuplicateKeys(false);
        options.setNestingDepthLimit(properties.getMaxYamlNestingDepth());
        options.setCodePointLimit(properties.getMaxYamlCodePoints());
        Object loaded;
        try {
            loaded = new Yaml(options).load(yamlText);
        } catch (RuntimeException exception) {
            throw new SkillPackageValidationException("SKILL.md Front Matter 解析失败", exception);
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw invalid("SKILL.md Front Matter 必须是对象");
        }

        // 递归校验YAML结构深度与大小
        validateYamlValue(loaded, 0);

        // 只允许指定的key，拒绝未知字段
        for (Object key : map.keySet()) {
            if (!(key instanceof String stringKey)
                    || !(stringKey.equals("name") || stringKey.equals("description") || stringKey.equals("allowed-tools"))) {
                throw invalid("SKILL.md Front Matter 包含未知字段");
            }
        }
        String name = stringValue(map.get("name"), "name");
        String description = stringValue(map.get("description"), "description");
        List<String> tools = new ArrayList<>();
        Object toolValue = map.get("allowed-tools");
        if (toolValue != null) {
            if (!(toolValue instanceof List<?> list)) {
                throw invalid("allowed-tools 必须是字符串数组");
            }
            for (Object value : list) {
                if (!(value instanceof String tool) || tool.isBlank() || tool.length() > SkillConstant.MAX_TOOL_NAME_LENGTH) {
                    throw invalid("allowed-tools 包含非法工具名");
                }
                tools.add(tool);
            }
        }
        return new FrontMatter(name, description, instruction, tools.stream().distinct().sorted().toList());
    }

    /**
     * 递归校验YAML节点：嵌套深度、集合大小，防御YAML炸弹
     * @param value 当前节点对象
     * @param depth 当前递归深度
     */
    private void validateYamlValue(Object value, int depth) {
        if (depth > properties.getMaxYamlNestingDepth()) {
            throw invalid("SKILL.md Front Matter 嵌套深度超过限制");
        }
        if (value instanceof Map<?, ?> map) {
            if (map.size() > properties.getMaxFileCount()) {
                throw invalid("SKILL.md Front Matter 字段数量超过限制");
            }
            map.forEach((key, nested) -> {
                validateYamlValue(key, depth + 1);
                validateYamlValue(nested, depth + 1);
            });
        } else if (value instanceof List<?> list) {
            if (list.size() > properties.getMaxFileCount()) {
                throw invalid("SKILL.md Front Matter 数组长度超过限制");
            }
            list.forEach(item -> validateYamlValue(item, depth + 1));
        } else if (value instanceof String string
                && string.codePointCount(0, string.length()) > properties.getMaxYamlCodePoints()) {
            throw invalid("SKILL.md Front Matter 文本长度超过限制");
        }
    }

    /**
     * 安全读取YAML字符串字段，校验非空、长度上限
     * @param value 原始对象
     * @param field 字段名，用于报错
     * @return 校验通过的字符串
     */
    private String stringValue(Object value, String field) {
        if (!(value instanceof String string) || string.isBlank() || string.length() > 500) {
            throw invalid("Front Matter 字段非法: " + field);
        }
        return string;
    }

    /**
     * 带大小上限从Zip流读取单文件，防护超大文件
     * @param input zip条目输入流
     * @param max 最大允许字节数
     * @param path 文件名，用于报错
     * @return 文件字节数组
     * @throws IOException IO / 超限抛出异常
     */
    private byte[] readBounded(InputStream input, long max, String path) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(max, 8192));
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > max) {
                throw invalid("文件大小超过限制: " + path);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    /**
     * 判断是否超过最大压缩比，用于检测压缩炸弹
     * @param declaredSize 解压后大小
     * @param compressedSize 压缩后大小
     * @param maxRatio 允许最大压缩比
     * @return true表示超出阈值
     */
    static boolean compressionRatioExceeded(long declaredSize, long compressedSize, int maxRatio) {
        return declaredSize >= 0 && compressedSize > 0
                && BigInteger.valueOf(declaredSize).compareTo(
                BigInteger.valueOf(compressedSize).multiply(BigInteger.valueOf(maxRatio))) > 0;
    }

    /**
     * ZIP路径归一化，防御路径穿越、绝对路径、反斜杠、空字节
     * @param raw 原始zip内entry name
     * @return 安全归一化后的包内路径
     */
    private String normalizePath(String raw) {
        if (raw == null || raw.isBlank() || raw.indexOf('\0') >= 0) {
            throw invalid("ZIP 存在非法路径");
        }
        String path = raw.replace('\\', '/');
        if (path.startsWith("/") || path.startsWith("//") || path.matches("^[A-Za-z]:.*")) {
            throw invalid("ZIP 不允许绝对路径: " + raw);
        }
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (segment.isBlank() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                throw invalid("ZIP 不允许父目录路径: " + raw);
            }
            segments.add(segment);
        }
        String normalized = String.join("/", segments);
        if (normalized.length() > properties.getMaxPathLength()) {
            throw invalid("ZIP 路径过长: " + raw);
        }
        return normalized;
    }

    /**
     * 判断是否为需要忽略的系统垃圾文件
     * @param path zip entry原始名称
     * @return true需要跳过该entry
     */
    private boolean isIgnored(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        return normalized.startsWith("__MACOSX/") || normalized.endsWith("/.DS_Store")
                || normalized.equals(".DS_Store");
    }

    /**
     * 获取文件扩展名，小写
     * @param path 文件路径
     * @return 后缀小写字符串，无后缀返回空串
     */
    private String extension(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 严格UTF‑8解码，遇到非法字节直接抛异常
     * @param bytes 原始字节
     * @param path 文件名，用于报错
     * @return 解码后的字符串
     */
    private String decodeUtf8(byte[] bytes, String path) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new SkillPackageValidationException("文件不是合法 UTF-8: " + path, exception);
        }
    }

    /**
     * 计算本地文件SHA‑256摘要
     * @param file 文件路径
     * @return 小写十六进制sha256
     * @throws IOException IO异常
     */
    private String sha256(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = messageDigest();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        }
    }

    /**
     * 计算字节数组SHA‑256摘要
     * @param bytes 字节数组
     * @return 小写十六进制sha256
     */
    private String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(messageDigest().digest(bytes));
    }

    /**
     * 获取SHA‑256 MessageDigest实例，JDK不支持则抛出IllegalStateException
     * @return MessageDigest
     */
    private MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    /**
     * 快捷构造校验异常
     * @param message 错误信息
     * @return SkillPackageValidationException
     */
    private SkillPackageValidationException invalid(String message) {
        return new SkillPackageValidationException(message);
    }

    /**
     * 内存临时记录：zip内单个文件的相对路径+原始字节
     * @param path 包内相对路径
     * @param content 文件原始字节
     */
    private record EntryMeta(String path, byte[] content) {
    }

    /**
     * SKILL.md Front‑Matter解析结果内存记录
     * @param name skill包名称
     * @param description 描述
     * @param instructionText 技能指令正文（YAML之后的全部文本）
     * @param allowedTools 声明允许的工具名称列表
     */
    private record FrontMatter(String name, String description, String instructionText, List<String> allowedTools) {
    }
}
