package dev.langchain4j.skills;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemSkillLoaderTest {

    @Test
    void should_load_skill_name_description_and_body() {
        Skill skill = loadSkill("skills/using-process-tool");

        assertThat(skill.name()).isEqualTo("using-process-tool");
        assertThat(skill.description()).isEqualTo("Describes how to correctly use 'process' tool");
        assertThat(skill.body()).contains(
                "When user asks you to use the 'process' tool",
                "call the 'generateId' tool",
                "call the 'process' tool with 3 arguments",
                "references/17.md",
                "references/25.md"
        );
    }

    @Test
    void should_load_files_recursively() {
        Skill skill = loadSkill("skills/using-process-tool");

        List<? extends SkillFile> files = skill.files();
        assertThat(files).hasSize(2);

        assertThat(files)
                .anySatisfy(file -> {
                    assertThat(file.path()).isEqualTo("references/17.md");
                    assertThat(file.body()).contains("code 17", "finish");
                })
                .anySatisfy(file -> {
                    assertThat(file.path()).isEqualTo("references/25.md");
                    assertThat(file.body()).contains("code 25", "reset");
                });
    }

    private Skill loadSkill(String resourcePath) {
        try {
            Path path = Paths.get(getClass().getClassLoader().getResource(resourcePath).toURI());
            return FileSystemSkillLoader.loadSkill(path);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
