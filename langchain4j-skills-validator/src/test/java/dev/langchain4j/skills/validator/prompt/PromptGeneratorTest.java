package dev.langchain4j.skills.validator.prompt;

import static org.assertj.core.api.Assertions.*;

import dev.langchain4j.skills.validator.error.SkillError;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PromptGeneratorTest {
    private final PromptGenerator generator = new PromptGenerator();

    @Test
    void shouldGenerateEmptyPromptForNoSkills() throws SkillError {
        String prompt = generator.toPrompt(List.of());

        assertThat(prompt).contains("<available_skills>").contains("</available_skills>");
    }

    @Test
    void shouldGeneratePromptForSingleSkill(@TempDir Path tempDir) throws Exception {
        String content = "---\nname: test-skill\ndescription: A test skill\n---\nBody";
        Files.writeString(tempDir.resolve("SKILL.md"), content);

        String prompt = generator.toPrompt(List.of(tempDir));

        assertThat(prompt)
                .contains("<available_skills>")
                .contains("<skill>")
                .contains("<name>")
                .contains("test-skill")
                .contains("</name>")
                .contains("<description>")
                .contains("A test skill")
                .contains("</description>")
                .contains("<location>")
                .contains("SKILL.md")
                .contains("</location>")
                .contains("</skill>")
                .contains("</available_skills>");
    }

    @Test
    void shouldGeneratePromptForMultipleSkills(@TempDir Path tempDir1, @TempDir Path tempDir2) throws Exception {
        Files.writeString(tempDir1.resolve("SKILL.md"), "---\nname: skill-one\ndescription: First skill\n---\nBody");
        Files.writeString(tempDir2.resolve("SKILL.md"), "---\nname: skill-two\ndescription: Second skill\n---\nBody");

        String prompt = generator.toPrompt(List.of(tempDir1, tempDir2));

        assertThat(prompt)
                .contains("skill-one")
                .contains("skill-two")
                .contains("First skill")
                .contains("Second skill");
    }

    @Test
    void shouldEscapeXmlSpecialCharacters(@TempDir Path tempDir) throws Exception {
        String content = "---\nname: test-skill\ndescription: Test & <skill> with \"quotes\"\n---\nBody";
        Files.writeString(tempDir.resolve("SKILL.md"), content);

        String prompt = generator.toPrompt(List.of(tempDir));

        assertThat(prompt)
                .contains("Test &amp; &lt;skill&gt; with &quot;quotes&quot;")
                .doesNotContain("Test & <skill> with \"quotes\"");
    }

    @Test
    void shouldIncludeSkillMdLocation(@TempDir Path tempDir) throws Exception {
        String content = "---\nname: test-skill\ndescription: A test skill\n---\nBody";
        Files.writeString(tempDir.resolve("SKILL.md"), content);

        String prompt = generator.toPrompt(List.of(tempDir));

        assertThat(prompt).contains("<location>").contains("SKILL.md").contains("</location>");
    }

    @Test
    void shouldHandleSkillWithAllMetadata(@TempDir Path tempDir) throws Exception {
        String content =
                "---\nname: test-skill\ndescription: A test skill\nlicense: Apache 2.0\ncompatibility: Java 17+\nallowed-tools: tool1, tool2\nmetadata:\n  key: value\n---\nBody";
        Files.writeString(tempDir.resolve("SKILL.md"), content);

        String prompt = generator.toPrompt(List.of(tempDir));

        assertThat(prompt).contains("test-skill").contains("A test skill");
    }

    @Test
    void shouldThrowSkillErrorIfSkillNotFound(@TempDir Path tempDir) {
        assertThatThrownBy(() -> generator.toPrompt(List.of(tempDir.resolve("nonexistent"))))
                .isInstanceOf(SkillError.class);
    }

    @Test
    void shouldFormatXmlWithProperStructure(@TempDir Path tempDir) throws Exception {
        String content = "---\nname: test-skill\ndescription: A test skill\n---\nBody";
        Files.writeString(tempDir.resolve("SKILL.md"), content);

        String prompt = generator.toPrompt(List.of(tempDir));

        String[] lines = prompt.split("\n");
        assertThat(lines[0]).isEqualTo("<available_skills>");
        assertThat(lines[lines.length - 1]).isEqualTo("</available_skills>");
    }
}
