package com.agentdoc.common.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.jar.Attributes;

/** 冻结当前进程全部 class/JAR 发布内容；不输出机器路径，不以单个入口代替传递实现。 */
public final class OnlineReleaseUtils {
    private OnlineReleaseUtils() { }
    private static final String IDENTITY = capture(System.getProperty("java.class.path"));
    public static String current() { return IDENTITY; }
    static String capture(String classPath) {
        try {
            Map<String, String> files = new TreeMap<>();
            files.put("runtime/java.version", System.getProperty("java.version"));
            files.put("runtime/java.vendor", System.getProperty("java.vendor"));
            var entries = new ArrayList<Path>();
            for (String entry : classPath.split(File.pathSeparator)) {
                entries.add(Path.of(entry).toAbsolutePath().normalize());
            }
            var visited = new HashSet<Path>();
            for (int index = 0; index < entries.size(); index++) {
                Path path = entries.get(index);
                if (!visited.add(path)) { continue; }
                if (!Files.exists(path)) { throw new IOException("发布路径缺失"); }
                if (Files.isDirectory(path)) {
                    try (var stream = Files.walk(path)) {
                        for (Path file : stream.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".class"))
                                .sorted().toList()) {
                            String key = "classes/" + path.relativize(file).toString().replace('\\', '/');
                            String previous = files.put(key, digest(file));
                            if (previous != null && !previous.equals(files.get(key))) {
                                throw new IllegalStateException("重复 class 发布身份冲突");
                            }
                        }
                    }
                } else if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
                    String hash = digest(path);
                    // Surefire 的启动 JAR 可含临时路径；只把其声明的真实发布内容纳入证明。
                    try (var jar = new JarFile(path.toFile())) {
                        var manifest = jar.getManifest();
                        String dependencies = manifest == null ? null : manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
                        if (dependencies != null && !dependencies.isBlank()) {
                            for (String dependency : dependencies.trim().split("\\s+")) {
                                entries.add(Path.of(path.toUri().resolve(dependency)).toAbsolutePath().normalize());
                            }
                        }
                        boolean testLauncher = path.getFileName().toString().startsWith("surefirebooter-")
                                && manifest != null && "org.apache.maven.surefire.booter.ForkedBooter".equals(
                                        manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS))
                                && System.getProperty("sun.java.command", "").contains(path.getFileName().toString());
                        if (!testLauncher) {
                            files.put("jars/" + hash, hash);
                        }
                    }
                }
            }
            if (files.size() == 2) { throw new IllegalStateException("发布身份不可取得"); }
            return OnlineProtocolUtils.hash("online.release", files);
        } catch (IOException failure) { throw new IllegalStateException("发布身份不可取得", failure); }
    }
    private static String digest(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = new byte[8192];
            for (int count; (count = input.read(bytes)) != -1;) { digest.update(bytes, 0, count); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
