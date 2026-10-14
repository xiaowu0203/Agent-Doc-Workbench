package com.agentdoc.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import static org.assertj.core.api.Assertions.*;

class OnlineReleaseUtilsTest {
    @TempDir Path directory;
    @Test void transitiveClassesAreIncludedAndMachinePathsAreExcluded() throws Exception {
        Path classes = Files.createDirectories(directory.resolve("first/com/example"));
        Files.write(classes.resolve("Entry.class"), new byte[]{1}); Files.write(classes.resolve("Transitive.class"), new byte[]{2});
        String first = OnlineReleaseUtils.capture(directory.resolve("first").toString());
        Path other = Files.createDirectories(directory.resolve("other/com/example"));
        Files.copy(classes.resolve("Entry.class"), other.resolve("Entry.class")); Files.copy(classes.resolve("Transitive.class"), other.resolve("Transitive.class"));
        assertThat(OnlineReleaseUtils.capture(directory.resolve("other").toString())).isEqualTo(first);
        Files.write(other.resolve("Transitive.class"), new byte[]{3});
        assertThat(OnlineReleaseUtils.capture(directory.resolve("other").toString())).isNotEqualTo(first);
    }
    @Test void manifestClassPathDependenciesAreTraversedAndMissingDependenciesFailClosed() throws Exception {
        Path dependency = Files.createDirectories(directory.resolve("dependent/com/example"));
        Files.write(dependency.resolve("Tool.class"), new byte[]{1});
        Path jar = directory.resolve("launcher.jar"); var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, "dependent/");
        try (var output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            output.putNextEntry(new JarEntry("Marker.class")); output.write(new byte[]{2}); output.closeEntry();
        }
        String first = OnlineReleaseUtils.capture(jar.toString()); Files.write(dependency.resolve("Tool.class"), new byte[]{3});
        assertThat(OnlineReleaseUtils.capture(jar.toString())).isNotEqualTo(first);
        assertThatThrownBy(() -> OnlineReleaseUtils.capture(directory.resolve("missing.jar").toString())).isInstanceOf(IllegalStateException.class);
    }
}
