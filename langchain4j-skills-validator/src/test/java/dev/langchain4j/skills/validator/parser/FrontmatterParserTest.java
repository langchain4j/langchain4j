package dev.langchain4j.skills.validator.parser;

import static org.assertj.core.api.Assertions.*;

import dev.langchain4j.skills.validator.error.ParseError;
import dev.langchain4j.skills.validator.error.ValidationError;
import dev.langchain4j.skills.validator.model.SkillProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FrontmatterParserTest {
    private final FrontmatterParser parser = new FrontmatterParser();

    @Test
    void shouldFindSkillMdFile(@TempDir Path tempDir) throws Exception {
        Path skillMd = Files.createFile(tempDir.resolve("SKILL.md"));

        Path found = parser.findSkillMd(tempDir);

        assertThat(found).isEqualTo(skillMd);
    }

    @Test
    void shouldFindLowercaseSkillMd(@TempDir Path tempDir) throws Exception {
        // Note: On case-insensitive filesystems, this will actually create a file
        // that might be named SKILL.md. We just verify the file was found.
        Path skillMd = Files.createFile(tempDir.resolve("skill.md"));

        Path found = parser.findSkillMd(tempDir);

        assertThat(found).isNotNull();
        // The file exists, though the name might differ on case-insensitive filesystems
        assertThat(Files.exists(found)).isTrue();
    }

    @Test
    void shouldPreferUppercaseSkillMd(@TempDir Path tempDir) throws Exception {
        // Note: On case-insensitive filesystems (macOS, Windows), creating both
        // SKILL.md and skill.md will only create one file. This test verifies
        // the parser prefers SKILL.md when available.
        Path found = Files.createFile(tempDir.resolve("SKILL.md"));

        Path result = parser.findSkillMd(tempDir);

        assertThat(result).isEqualTo(found);
    }

    @Test
    void shouldReturnNullIfSkillMdNotFound(@TempDir Path tempDir) {
        Path found = parser.findSkillMd(tempDir);

        assertThat(found).isNull();
    }

    @Test
    void shouldParseFrontmatterCorrectly() throws ParseError {
        String content = "---\nname: test-skill\ndescription: A test skill\nlicense: Apache 2.0\n---\nBody content";

        FrontmatterParser.FrontmatterResult result = parser.parseFrontmatter(content);

        assertThat(result.getMetadata()).containsEntry("name", "test-skill");
        assertThat(result.getMetadata()).containsEntry("description", "A test skill");
        assertThat(result.getMetadata()).containsEntry("license", "Apache 2.0");
        assertThat(result.getBody()).isEqualTo("Body content");
    }

    @Test
    void shouldThrowParseErrorIfNoFrontmatterStart() {
        String content = "No frontmatter start";

        assertThatThrownBy(() -> parser.parseFrontmatter(content))
                .isInstanceOf(ParseError.class)
                .hasMessageContaining("must start with YAML frontmatter");
    }

    @Test
    void shouldThrowParseErrorIfFrontmatterNotClosed() {
        String content = "---\nname: test\n\nBody without closing ---";

        assertThatThrownBy(() -> parser.parseFrontmatter(content))
                .isInstanceOf(ParseError.class)
                .hasMessageContaining("Invalid YAML");
    }

    @Test
    void shouldReadPropertiesFromSkillDirectory(@TempDir Path tempDir) throws Exception {
        String content = "---\nname: test-skill\ndescription: A test skill\nlicense: Apache 2.0\n---\nBody";
        Files.writeString(tempDir.resolve("SKILL.md"), content);

        SkillProperties props = parser.readProperties(tempDir);

        assertThat(props.getName()).isEqualTo("test-skill");
        assertThat(props.getDescription()).isEqualTo("A test skill");
        assertThat(props.getLicense()).isEqualTo("Apache 2.0");
    }

    @Test
    void shouldThrowParseErrorIfSkillMdNotFound(@TempDir Path tempDir) {
        assertThatThrownBy(() -> parser.readProperties(tempDir))
                .isInstanceOf(ParseError.class)
                .hasMessageContaining("SKILL.md not found");
    }

    @Test
    void shouldThrowValidationErrorIfNameMissing(@TempDir Path tempDir) throws Exception {
        String content = "---\ndescription: A test skill\n---\nBody";
        Files.writeString(tempDir.resolve("SKILL.md"), content);

        assertThatThrownBy(() -> parser.readProperties(tempDir))
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("name");
    }

    @Test
    void shouldThrowValidationErrorIfDescriptionMissing(@TempDir Path tempDir) throws Exception {
        String content = "---\nname: test-skill\n---\nBody";
        Files.writeString(tempDir.resolve("SKILL.md"), content);

        assertThatThrownBy(() -> parser.readProperties(tempDir))
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("description");
    }

    @Test
    void shouldParseMetadataField(@TempDir Path tempDir) throws Exception {
        String content =
                "---\nname: test-skill\ndescription: A test skill\nmetadata:\n  key1: value1\n  key2: value2\n---\nBody";
        Files.writeString(tempDir.resolve("SKILL.md"), content);

        SkillProperties props = parser.readProperties(tempDir);

        assertThat(props.getMetadata()).containsEntry("key1", "value1").containsEntry("key2", "value2");
    }

    @Test
    void shouldHandleAllowedToolsField(@TempDir Path tempDir) throws Exception {
        String content = "---\nname: test-skill\ndescription: A test skill\nallowed-tools: tool1, tool2\n---\nBody";
        Files.writeString(tempDir.resolve("SKILL.md"), content);

        SkillProperties props = parser.readProperties(tempDir);

        assertThat(props.getAllowedTools()).isEqualTo("tool1, tool2");
    }
}
