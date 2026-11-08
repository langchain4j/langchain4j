package dev.langchain4j.skills;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

class FileSystemSkillLoaderTest {

    private static final String SYMBOLIC_LINKS_NEED_PRIVILEGES_ON_WINDOWS =
            "creating symbolic links on Windows requires special privileges";

    @Test
    void should_load_all_skills_from_directory() {
        List<FileSystemSkill> skills = FileSystemSkillLoader.loadSkills(toPath("skills"));

        assertThat(skills)
                .extracting(Skill::name)
                .containsExactlyInAnyOrder("greeting-user", "test-skill", "using-process-tool");
    }

    @Test
    void should_skip_directories_without_skill_md() {
        // using-process-tool/references/ has no SKILL.md, so loadSkills should return nothing
        List<FileSystemSkill> skills = FileSystemSkillLoader.loadSkills(toPath("skills/using-process-tool"));

        assertThat(skills).isEmpty();
    }

    @Test
    void should_load_skill_name_and_description_and_content() {
        Skill skill = loadSkill("skills/using-process-tool");

        assertThat(skill.name()).isEqualTo("using-process-tool");
        assertThat(skill.description()).isEqualTo("Describes how to correctly use 'process' tool");
        assertThat(skill.content()).contains(
                "When user asks you to use the 'process' tool",
                "call the 'generate' tool",
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

    @Test
    void should_skip_empty_resources() {
        Skill skill = FileSystemSkillLoader.loadSkill(toPath("skills/test-skill"));

        assertThat(skill.resources().stream().map(SkillResource::relativePath))
                .doesNotContain("references/empty.md")
                .containsExactly("references/full.md");
    }

    @Test
    void should_skip_binary_files() {
        Skill skill = FileSystemSkillLoader.loadSkill(toPath("skills/test-skill"));

        assertThat(skill.resources().stream().map(SkillResource::relativePath))
                .doesNotContain("references/image.png")
                .containsExactly("references/full.md");
    }

    @Test
    void should_load_files_whose_names_start_with_scripts_outside_of_scripts_directory(@TempDir Path tempDir)
            throws IOException {
        Path skillDirectory = writeSkill(tempDir.resolve("my-skill"));
        writeFile(skillDirectory.resolve("scripts/run.py"), "print('hello')");
        writeFile(skillDirectory.resolve("scripts-guide.md"), "guide");

        Skill skill = FileSystemSkillLoader.loadSkill(skillDirectory);

        assertThat(skill.resources()).extracting(SkillResource::relativePath).containsExactly("scripts-guide.md");
    }

    @Test
    @DisabledOnOs(value = WINDOWS, disabledReason = SYMBOLIC_LINKS_NEED_PRIVILEGES_ON_WINDOWS)
    void should_load_symbolic_link_pointing_inside_of_skill_directory(@TempDir Path tempDir) throws IOException {
        Path skillDirectory = writeSkill(tempDir.resolve("my-skill"));
        writeFile(skillDirectory.resolve("references/v2.md"), "version 2");
        Files.createSymbolicLink(skillDirectory.resolve("latest.md"), Path.of("references/v2.md"));

        Skill skill = FileSystemSkillLoader.loadSkill(skillDirectory);

        assertThat(skill.resources())
                .extracting(SkillResource::relativePath, SkillResource::content)
                .containsExactlyInAnyOrder(tuple("latest.md", "version 2"), tuple("references/v2.md", "version 2"));
    }

    @Test
    @DisabledOnOs(value = WINDOWS, disabledReason = SYMBOLIC_LINKS_NEED_PRIVILEGES_ON_WINDOWS)
    void should_fail_when_resource_is_relative_symbolic_link_pointing_outside_of_skill_directory(
            @TempDir Path tempDir) throws IOException {
        writeFile(tempDir.resolve("outside.md"), "outside");
        Path skillDirectory = writeSkill(tempDir.resolve("my-skill"));
        Files.createSymbolicLink(skillDirectory.resolve("notes.md"), Path.of("../outside.md"));

        assertThatThrownBy(() -> FileSystemSkillLoader.loadSkill(skillDirectory))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("notes.md")
                .hasMessageContaining("outside of the skill directory")
                .hasMessageNotContaining("outside.md");
    }

    @Test
    @DisabledOnOs(value = WINDOWS, disabledReason = SYMBOLIC_LINKS_NEED_PRIVILEGES_ON_WINDOWS)
    void should_fail_when_resource_is_absolute_symbolic_link_pointing_outside_of_skill_directory(
            @TempDir Path tempDir) throws IOException {
        Path outside = writeFile(tempDir.resolve("outside.md"), "outside");
        Path skillDirectory = writeSkill(tempDir.resolve("my-skill"));
        Files.createDirectories(skillDirectory.resolve("references"));
        Files.createSymbolicLink(skillDirectory.resolve("references/notes.md"), outside.toAbsolutePath());

        assertThatThrownBy(() -> FileSystemSkillLoader.loadSkill(skillDirectory))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("notes.md")
                .hasMessageContaining("outside of the skill directory")
                .hasMessageNotContaining("outside.md");
    }

    @Test
    @DisabledOnOs(value = WINDOWS, disabledReason = SYMBOLIC_LINKS_NEED_PRIVILEGES_ON_WINDOWS)
    void should_fail_when_SKILL_md_is_symbolic_link_pointing_outside_of_skill_directory(@TempDir Path tempDir)
            throws IOException {
        writeSkill(tempDir.resolve("my-skill2")); // shares the "my-skill" name prefix, but is a different directory
        Path skillDirectory = Files.createDirectories(tempDir.resolve("my-skill"));
        Files.createSymbolicLink(skillDirectory.resolve("SKILL.md"), Path.of("../my-skill2/SKILL.md"));

        assertThatThrownBy(() -> FileSystemSkillLoader.loadSkill(skillDirectory))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKILL.md")
                .hasMessageContaining("outside of the skill directory")
                .hasMessageNotContaining("my-skill2");
    }

    @Test
    @DisabledOnOs(value = WINDOWS, disabledReason = SYMBOLIC_LINKS_NEED_PRIVILEGES_ON_WINDOWS)
    void should_fail_when_loading_all_skills_and_one_has_symbolic_link_pointing_outside_of_skill_directory(
            @TempDir Path tempDir) throws IOException {
        writeFile(tempDir.resolve("outside.md"), "outside");
        writeSkill(tempDir.resolve("skills/valid-skill"));
        Path skillDirectory = writeSkill(tempDir.resolve("skills/my-skill"));
        Files.createSymbolicLink(skillDirectory.resolve("notes.md"), Path.of("../../outside.md"));

        assertThatThrownBy(() -> FileSystemSkillLoader.loadSkills(tempDir.resolve("skills")))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("notes.md")
                .hasMessageContaining("outside of the skill directory");
    }

    @Test
    @DisabledOnOs(value = WINDOWS, disabledReason = SYMBOLIC_LINKS_NEED_PRIVILEGES_ON_WINDOWS)
    void should_not_follow_symbolic_link_to_directory_outside_of_skill_directory(@TempDir Path tempDir)
            throws IOException {
        writeFile(tempDir.resolve("outside/ref.md"), "outside");
        Path skillDirectory = writeSkill(tempDir.resolve("my-skill"));
        Files.createSymbolicLink(skillDirectory.resolve("references"), Path.of("../outside"));

        Skill skill = FileSystemSkillLoader.loadSkill(skillDirectory);

        assertThat(skill.resources()).isEmpty();
    }

    @Test
    @DisabledOnOs(value = WINDOWS, disabledReason = SYMBOLIC_LINKS_NEED_PRIVILEGES_ON_WINDOWS)
    void should_load_resources_when_skill_directory_is_symbolic_link(@TempDir Path tempDir) throws IOException {
        Path realSkillDirectory = writeSkill(tempDir.resolve("shared/my-skill"));
        writeFile(realSkillDirectory.resolve("references/ref.md"), "reference");
        Path skillsDirectory = Files.createDirectories(tempDir.resolve("skills"));
        Path skillDirectory =
                Files.createSymbolicLink(skillsDirectory.resolve("my-skill"), Path.of("../shared/my-skill"));

        List<FileSystemSkill> skills = FileSystemSkillLoader.loadSkills(skillsDirectory);

        assertThat(skills).singleElement().satisfies(skill -> {
            assertThat(skill.basePath()).isEqualTo(skillDirectory);
            assertThat(skill.resources())
                    .extracting(SkillResource::relativePath, SkillResource::content)
                    .containsExactly(tuple("references/ref.md", "reference"));
        });
    }

    private static Path writeSkill(Path skillDirectory) throws IOException {
        writeFile(skillDirectory.resolve("SKILL.md"), """
                ---
                name: %s
                description: Test skill
                ---

                Instructions
                """.formatted(skillDirectory.getFileName()));
        return skillDirectory;
    }

    private static Path writeFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
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
