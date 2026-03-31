package dev.langchain4j.skills;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClassPathSkillLoaderTest {

    @Test
    void should_load_all_skills_from_directory() {
        List<FileSystemSkill> skills = ClassPathSkillLoader.loadSkills("skills");

        assertThat(skills)
                .extracting(Skill::name)
                .containsExactlyInAnyOrder("greeting-user", "test-skill", "using-process-tool");
    }

    @Test
    void should_skip_directories_without_skill_md() {
        List<FileSystemSkill> skills = ClassPathSkillLoader.loadSkills("skills/using-process-tool");

        assertThat(skills).isEmpty();
    }

    @Test
    void should_load_skill_name_and_description_and_content() {
        Skill skill = ClassPathSkillLoader.loadSkill("skills/using-process-tool");

        assertThat(skill.name()).isEqualTo("using-process-tool");
        assertThat(skill.description()).isEqualTo("Describes how to correctly use 'process' tool");
        assertThat(skill.content())
                .contains(
                        "When user asks you to use the 'process' tool",
                        "call the 'generate' tool",
                        "call the 'process' tool with 3 arguments",
                        "references/17.md",
                        "references/25.md");
    }

    @Test
    void should_load_resources_recursively() {
        Skill skill = ClassPathSkillLoader.loadSkill("skills/using-process-tool");

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
        Skill skill = ClassPathSkillLoader.loadSkill("skills/greeting-user");

        assertThat(skill.resources()).hasSize(1);
        assertThat(skill.resources().get(0).relativePath()).isEqualTo("references/processing-result.md");
    }

    @Test
    void should_ignore_empty_resources() {
        Skill skill = ClassPathSkillLoader.loadSkill("skills/test-skill");

        assertThat(skill.resources().stream().map(SkillResource::relativePath))
                .doesNotContain("references/empty.md")
                .containsExactly("references/full.md");
    }

    @Test
    void should_load_skill_with_custom_classloader() {
        Skill skill = ClassPathSkillLoader.loadSkill(
                "skills/using-process-tool", Thread.currentThread().getContextClassLoader());

        assertThat(skill.name()).isEqualTo("using-process-tool");
    }
}
