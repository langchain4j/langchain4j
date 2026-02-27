package dev.langchain4j.skills;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemSkillLoaderTest {

    @Test
    void should_load_all_skills_from_directory() {
        List<Skill> skills = FileSystemSkillLoader.loadSkills(toPath("skills"));

        assertThat(skills)
                .extracting(Skill::name)
                .containsExactlyInAnyOrder("docx", "greeting-user", "mcp-builder", "using-process-tool");
    }

    @Test
    void should_skip_directories_without_skill_md() {
        // using-process-tool/references/ has no SKILL.md, so loadSkills should return nothing
        List<Skill> skills = FileSystemSkillLoader.loadSkills(toPath("skills/using-process-tool"));

        assertThat(skills).isEmpty();
    }

    @Test
    void should_load_skill_name_and_description_and_content() {
        Skill skill = loadSkill("skills/using-process-tool");

        assertThat(skill.name()).isEqualTo("using-process-tool");
        assertThat(skill.description()).isEqualTo("Describes how to correctly use 'process' tool");
        assertThat(skill.content()).contains(
                "When user asks you to use the 'process' tool",
                "call the 'generateId' tool",
                "call the 'process' tool with 3 arguments",
                "references/17.md",
                "references/25.md"
        );
    }

    @Test
    void should_load_resources_recursively() {
        Skill skill = loadSkill("skills/using-process-tool");

        List<? extends SkillResource> files = skill.resources();
        assertThat(files).hasSize(2);

        assertThat(files)
                .anySatisfy(file -> {
                    assertThat(file.relativePath()).isEqualTo("references/17.md");
                    assertThat(file.content()).contains("code 17", "finish");
                })
                .anySatisfy(file -> {
                    assertThat(file.relativePath()).isEqualTo("references/25.md");
                    assertThat(file.content()).contains("code 25", "reset");
                });
    }

    @Test
    void should_not_load_scripts_as_skill_resources() {
        Skill skill = loadSkill("skills/greeting-user");

        assertThat(skill.resources()).hasSize(1);
        assertThat(skill.resources().get(0).relativePath()).isEqualTo("references/processing-result.md");
    }

    @Test
    void should_store_directory_when_loaded_from_filesystem() {
        Path expectedDir = toPath("skills/using-process-tool");
        Skill skill = FileSystemSkillLoader.loadSkill(expectedDir);

        assertThat(skill).isInstanceOf(FileSystemSkill.class);
        assertThat(((FileSystemSkill) skill).basePath()).isEqualTo(expectedDir);
    }

    private Skill loadSkill(String resourcePath) {
        return FileSystemSkillLoader.loadSkill(toPath(resourcePath));
    }

    private Path toPath(String resourcePath) {
        try {
            return Paths.get(getClass().getClassLoader().getResource(resourcePath).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
