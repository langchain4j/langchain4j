package dev.langchain4j.agentskills.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentskills.Skill;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link FileSystemSkillLoader}.
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 */
class FileSystemSkillLoaderTest {

    @Test
    void should_load_skill_with_complete_frontmatter(@TempDir Path tempDir) throws IOException {
        // given
        Path skillDir = tempDir.resolve("pdf-processing");
        Files.createDirectories(skillDir);

        String skillMd =
                """
                ---
                name: pdf-processing
                description: Process PDF files
                license: MIT
                compatibility: langchain4j >= 1.0.0
                allowed-tools: python node
                metadata:
                  author: John Doe
                  version: 1.0.0
                ---
                # PDF Processing Skill
                
                Use this skill to process PDF files.
                """;

        Files.writeString(skillDir.resolve("SKILL.md"), skillMd);
        FileSystemSkillLoader loader = new FileSystemSkillLoader();

        // when
        Skill skill = loader.loadSkill(skillDir);

        // then
        assertThat(skill).isNotNull();
        assertThat(skill.name()).isEqualTo("pdf-processing");
        assertThat(skill.description()).isEqualTo("Process PDF files");
        assertThat(skill.license()).isEqualTo("MIT");
        assertThat(skill.compatibility()).isEqualTo("langchain4j >= 1.0.0");
        assertThat(skill.allowedTools()).containsExactly("python", "node");
        assertThat(skill.metadata()).containsEntry("author", "John Doe");
        assertThat(skill.metadata()).containsEntry("version", "1.0.0");
        assertThat(skill.instructions()).contains("# PDF Processing Skill");
        assertThat(skill.path()).isEqualTo(skillDir);
    }

    @Test
    void should_load_skill_with_minimal_frontmatter(@TempDir Path tempDir) throws IOException {
        // given
        Path skillDir = tempDir.resolve("minimal-skill");
        Files.createDirectories(skillDir);

        String skillMd =
                """
                ---
                name: minimal-skill
                description: A minimal skill
                ---
                # Minimal
                """;

        Files.writeString(skillDir.resolve("SKILL.md"), skillMd);
        FileSystemSkillLoader loader = new FileSystemSkillLoader();

        // when
        Skill skill = loader.loadSkill(skillDir);

        // then
        assertThat(skill).isNotNull();
        assertThat(skill.name()).isEqualTo("minimal-skill");
        assertThat(skill.description()).isEqualTo("A minimal skill");
        assertThat(skill.license()).isNull();
        assertThat(skill.compatibility()).isNull();
        assertThat(skill.allowedTools()).isNullOrEmpty();
        assertThat(skill.metadata()).isNullOrEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "PDF_Processing, false",
        "pdf processing, false",
        "pdf.processing, false",
        "pdf-processing, true",
        "data-analysis-v2, true",
        "skill123, true"
    })
    void should_validate_skill_name_format(String skillName, boolean isValid, @TempDir Path tempDir)
            throws IOException {
        // given
        Path skillDir = tempDir.resolve("test-skill");
        Files.createDirectories(skillDir);

        String skillMd = String.format(
                """
                ---
                name: %s
                description: Test skill
                ---
                # Test
                """,
                skillName);

        Files.writeString(skillDir.resolve("SKILL.md"), skillMd);
        FileSystemSkillLoader loader = new FileSystemSkillLoader();

        // when-then
        if (isValid) {
            Skill skill = loader.loadSkill(skillDir);
            assertThat(skill).isNotNull();
            assertThat(skill.name()).isEqualTo(skillName);
        } else {
            assertThatThrownBy(() -> loader.loadSkill(skillDir))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name");
        }
    }

    @Test
    void should_throw_exception_when_description_exceeds_max_length(@TempDir Path tempDir) throws IOException {
        // given
        Path skillDir = tempDir.resolve("long-desc-skill");
        Files.createDirectories(skillDir);

        String longDescription = "a".repeat(1025); // exceeds 1024 limit
        String skillMd = String.format(
                """
                ---
                name: long-desc
                description: %s
                ---
                # Test
                """,
                longDescription);

        Files.writeString(skillDir.resolve("SKILL.md"), skillMd);
        FileSystemSkillLoader loader = new FileSystemSkillLoader();

        // when-then
        assertThatThrownBy(() -> loader.loadSkill(skillDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Description")
                .hasMessageContaining("1024");
    }

    @Test
    void should_throw_exception_when_compatibility_exceeds_max_length(@TempDir Path tempDir) throws IOException {
        // given
        Path skillDir = tempDir.resolve("long-compat-skill");
        Files.createDirectories(skillDir);

        String longCompatibility = "a".repeat(501); // exceeds 500 limit
        String skillMd = String.format(
                """
                ---
                name: long-compat
                description: Test skill
                compatibility: %s
                ---
                # Test
                """,
                longCompatibility);

        Files.writeString(skillDir.resolve("SKILL.md"), skillMd);
        FileSystemSkillLoader loader = new FileSystemSkillLoader();

        // when-then
        assertThatThrownBy(() -> loader.loadSkill(skillDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Compatibility")
                .hasMessageContaining("500");
    }

    @Test
    void should_parse_allowed_tools_with_various_separators(@TempDir Path tempDir) throws IOException {
        // given
        Path skillDir = tempDir.resolve("tool-skill");
        Files.createDirectories(skillDir);

        String skillMd =
                """
                ---
                name: tool-skill
                description: Skill with tools
                allowed-tools: python  node    bash
                ---
                # Test
                """;

        Files.writeString(skillDir.resolve("SKILL.md"), skillMd);
        FileSystemSkillLoader loader = new FileSystemSkillLoader();

        // when
        Skill skill = loader.loadSkill(skillDir);

        // then
        assertThat(skill.allowedTools()).hasSize(3).containsExactly("python", "node", "bash");
    }

    @Test
    void should_return_null_when_skill_file_does_not_exist(@TempDir Path tempDir) {
        // given
        Path nonExistentDir = tempDir.resolve("non-existent");
        FileSystemSkillLoader loader = new FileSystemSkillLoader();

        // when
        Skill skill = loader.loadSkill(nonExistentDir);

        // then
        assertThat(skill).isNull();
    }

    @Test
    void should_return_null_for_malformed_frontmatter_missing_opening_delimiter(@TempDir Path tempDir)
            throws IOException {
        // given
        Path skillDir = tempDir.resolve("malformed-skill");
        Files.createDirectories(skillDir);

        String skillMd =
                """
                name: malformed-skill
                description: Missing opening delimiter
                ---
                # Test
                """;

        Files.writeString(skillDir.resolve("SKILL.md"), skillMd);
        FileSystemSkillLoader loader = new FileSystemSkillLoader();

        // when
        Skill skill = loader.loadSkill(skillDir);

        // then
        assertThat(skill).isNull();
    }

    @Test
    void should_return_null_for_malformed_frontmatter_missing_closing_delimiter(@TempDir Path tempDir)
            throws IOException {
        // given
        Path skillDir = tempDir.resolve("malformed-skill2");
        Files.createDirectories(skillDir);

        String skillMd =
                """
                ---
                name: malformed-skill
                description: Missing closing delimiter
                # Test content without closing delimiter
                """;

        Files.writeString(skillDir.resolve("SKILL.md"), skillMd);
        FileSystemSkillLoader loader = new FileSystemSkillLoader();

        // when
        Skill skill = loader.loadSkill(skillDir);

        // then
        assertThat(skill).isNull();
    }

    @Test
    void should_return_null_for_empty_file(@TempDir Path tempDir) throws IOException {
        // given
        Path skillDir = tempDir.resolve("empty-skill");
        Files.createDirectories(skillDir);

        Files.writeString(skillDir.resolve("SKILL.md"), "");
        FileSystemSkillLoader loader = new FileSystemSkillLoader();

        // when
        Skill skill = loader.loadSkill(skillDir);

        // then
        assertThat(skill).isNull();
    }

    @Test
    void should_load_multiple_skills_from_directory(@TempDir Path tempDir) throws IOException {
        // given
        createSkill(tempDir, "pdf-processing", "Process PDFs");
        createSkill(tempDir, "web-search", "Search the web");
        createSkill(tempDir, "data-analysis", "Analyze data");

        FileSystemSkillLoader loader = new FileSystemSkillLoader();

        // when
        List<Skill> skills = loader.loadSkillsFromDirectory(tempDir);

        // then
        assertThat(skills).hasSize(3);
        assertThat(skills).extracting(Skill::name).containsExactlyInAnyOrder("pdf-processing", "web-search", "data-analysis");
    }

    @Test
    void should_return_empty_list_for_empty_directory(@TempDir Path tempDir) {
        // given
        FileSystemSkillLoader loader = new FileSystemSkillLoader();

        // when
        List<Skill> skills = loader.loadSkillsFromDirectory(tempDir);

        // then
        assertThat(skills).isEmpty();
    }

    @Test
    void should_return_empty_list_when_directory_does_not_exist(@TempDir Path tempDir) {
        // given
        Path nonExistent = tempDir.resolve("non-existent");
        FileSystemSkillLoader loader = new FileSystemSkillLoader();

        // when
        List<Skill> skills = loader.loadSkillsFromDirectory(nonExistent);

        // then
        assertThat(skills).isEmpty();
    }

    // Helper method
    private void createSkill(Path parentDir, String skillName, String description) throws IOException {
        Path skillDir = parentDir.resolve(skillName);
        Files.createDirectories(skillDir);

        String skillMd = String.format(
                """
                ---
                name: %s
                description: %s
                ---
                # %s
                """,
                skillName, description, skillName);

        Files.writeString(skillDir.resolve("SKILL.md"), skillMd);
    }
}
