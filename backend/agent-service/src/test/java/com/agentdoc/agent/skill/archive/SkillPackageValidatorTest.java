package com.agentdoc.agent.skill.archive;

import com.agentdoc.agent.config.SkillPackageProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillPackageValidatorTest {

    private final SkillPackageValidator validator = new SkillPackageValidator(new SkillPackageProperties());

    @Test
    void acceptsFlatSkillPackageAndClassifiesEntries() throws IOException {
        Path zip = createZip(Map.of(
                "audit-document-skill/SKILL.md",
                "---\nname: audit-document-skill\ndescription: Audit documents\n"
                        + "allowed-tools: [mcp__docs__read]\n---\nFollow the audit rules.\n",
                "audit-document-skill/references/audit-rule.md", "# Rules\n",
                "audit-document-skill/scripts/parse_doc.py", "print('stored only')\n"));

        ParsedSkillPackage parsed = validator.validate(zip);

        assertThat(parsed.name()).isEqualTo("audit-document-skill");
        assertThat(parsed.allowedTools()).containsExactly("mcp__docs__read");
        assertThat(parsed.entries()).extracting(SkillPackageEntry::path)
                .containsExactlyInAnyOrder("SKILL.md", "references/audit-rule.md", "scripts/parse_doc.py");
        assertThat(parsed.entries().stream().filter(SkillPackageEntry::runtimeReadable)
                .map(SkillPackageEntry::path)).containsExactly("references/audit-rule.md");
    }

    @Test
    void acceptsAllOptionalDirectoriesAndIgnoresMacMetadata() throws IOException {
        Path zip = createZip(Map.of(
                "audit-document-skill/examples/sample.json", "{}",
                "audit-document-skill/assets/template.bin", "asset",
                "audit-document-skill/scripts/parse_doc.py", "print('ok')",
                "audit-document-skill/references/rule.txt", "rule",
                "audit-document-skill/SKILL.md", "---\nname: audit-document-skill\ndescription: Audit\n---\nUse it.\n",
                "audit-document-skill/.DS_Store", "ignored",
                "__MACOSX/audit-document-skill/._SKILL.md", "ignored"));

        assertThat(validator.validate(zip).entries()).extracting(SkillPackageEntry::path)
                .containsExactlyInAnyOrder("SKILL.md", "examples/sample.json", "assets/template.bin",
                        "scripts/parse_doc.py", "references/rule.txt");
    }

    @Test
    void rejectsMultipleTopLevelDirectories() throws IOException {
        Path zip = createZip(Map.of(
                "one/SKILL.md", "---\nname: one\ndescription: One\n---\n",
                "two/file.txt", "bad"));

        assertThatThrownBy(() -> validator.validate(zip))
                .isInstanceOf(SkillPackageValidationException.class)
                .hasMessageContaining("顶层");
    }

    @Test
    void rejectsRootNameMismatch() throws IOException {
        Path zip = createZip(Map.of(
                "audit-document-skill/SKILL.md", "---\nname: other-skill\ndescription: Other\n---\n"));

        assertThatThrownBy(() -> validator.validate(zip))
                .isInstanceOf(SkillPackageValidationException.class)
                .hasMessageContaining("一致");
    }

    @Test
    void rejectsUnknownDirectoryAndUnsafePath() throws IOException {
        Path unknown = createZip(Map.of(
                "skill/SKILL.md", "---\nname: skill\ndescription: Skill\n---\n",
                "skill/docs/readme.md", "not allowed"));
        assertThatThrownBy(() -> validator.validate(unknown))
                .isInstanceOf(SkillPackageValidationException.class)
                .hasMessageContaining("不允许");

        Path traversal = createZip(Map.of(
                "skill/SKILL.md", "---\nname: skill\ndescription: Skill\n---\n",
                "skill/../secret.txt", "bad"));
        assertThatThrownBy(() -> validator.validate(traversal))
                .isInstanceOf(SkillPackageValidationException.class)
                .hasMessageContaining("父目录");
    }

    @Test
    void rejectsMissingSkillFileAndInvalidScriptExtension() throws IOException {
        Path missing = createZip(Map.of("skill/references/rule.md", "rule"));
        assertThatThrownBy(() -> validator.validate(missing))
                .isInstanceOf(SkillPackageValidationException.class)
                .hasMessageContaining("SKILL.md");

        Path invalidScript = createZip(Map.of(
                "skill/SKILL.md", "---\nname: skill\ndescription: Skill\n---\n",
                "skill/scripts/run.exe", "bad"));
        assertThatThrownBy(() -> validator.validate(invalidScript))
                .isInstanceOf(SkillPackageValidationException.class)
                .hasMessageContaining("扩展名");
    }

    @Test
    void rejectsYamlNestingDepth() throws IOException {
        StringBuilder frontMatter = new StringBuilder("---\nname: nested\ndescription: Nested\nx:\n");
        for (int i = 0; i < 25; i++) {
            frontMatter.append("  ".repeat(i + 1)).append("x:\n");
        }
        frontMatter.append("  ".repeat(26)).append("value\n---\n");
        Path zip = createZip(Map.of("nested/SKILL.md", frontMatter.toString()));

        assertThatThrownBy(() -> validator.validate(zip))
                .isInstanceOf(SkillPackageValidationException.class);
    }

    @Test
    void appliesYamlCodePointLimitIndependentlyFromUtf8ByteLimit() throws IOException {
        SkillPackageProperties limited = new SkillPackageProperties();
        limited.setMaxYamlCodePoints(10);
        Path zip = createZip(Map.of("unicode/SKILL.md",
                "---\nname: unicode\ndescription: 中文文本\n---\n"));

        assertThatThrownBy(() -> new SkillPackageValidator(limited).validate(zip))
                .isInstanceOf(SkillPackageValidationException.class)
                .hasMessageContaining("文本长度");
    }

    @Test
    void checksCompressionRatioWithoutIntegerTruncation() {
        assertThat(SkillPackageValidator.compressionRatioExceeded(999, 10, 100)).isFalse();
        assertThat(SkillPackageValidator.compressionRatioExceeded(1000, 10, 100)).isFalse();
        assertThat(SkillPackageValidator.compressionRatioExceeded(1001, 10, 100)).isTrue();
    }

    private Path createZip(Map<String, String> entries) throws IOException {
        Path zip = Files.createTempFile("skill-validator-", ".zip");
        try (OutputStream output = Files.newOutputStream(zip); ZipOutputStream archive = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                archive.putNextEntry(new ZipEntry(entry.getKey()));
                archive.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                archive.closeEntry();
            }
        }
        zip.toFile().deleteOnExit();
        return zip;
    }
}
