package dev.langchain4j.skills;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    @Test
    void should_load_skill_from_jar(@TempDir Path tempDir) throws IOException {
        Path jarFile = createSkillsJar(tempDir);
        try (URLClassLoader classLoader =
                new URLClassLoader(new URL[] {jarFile.toUri().toURL()}, null)) {

            Skill skill = ClassPathSkillLoader.loadSkill("skills/using-process-tool", classLoader);

            assertThat(skill.name()).isEqualTo("using-process-tool");
            assertThat(skill.description()).isEqualTo("Describes how to correctly use 'process' tool");
            assertThat(skill.content()).contains("call the 'process' tool with 3 arguments");
            assertThat(skill.resources()).hasSize(2);
            assertThat(skill.resources())
                    .anySatisfy(file -> {
                        assertThat(file.relativePath()).isEqualTo("references/17.md");
                        assertThat(file.content()).contains("code 17");
                    })
                    .anySatisfy(file -> {
                        assertThat(file.relativePath()).isEqualTo("references/25.md");
                        assertThat(file.content()).contains("code 25");
                    });
        }
    }

    @Test
    void should_load_all_skills_from_jar(@TempDir Path tempDir) throws IOException {
        Path jarFile = createSkillsJar(tempDir);
        try (URLClassLoader classLoader =
                new URLClassLoader(new URL[] {jarFile.toUri().toURL()}, null)) {

            List<FileSystemSkill> skills = ClassPathSkillLoader.loadSkills("skills", classLoader);

            assertThat(skills)
                    .extracting(Skill::name)
                    .containsExactlyInAnyOrder("greeting-user", "test-skill", "using-process-tool");
        }
    }

    /**
     * Packages the contents of src/test/resources/skills into a JAR file and stores it in the given
     * temporary directory. The goal is to be able to test that the ClassPathSkillLoader
     * works properly when skills are packaged inside a JAR.
     */
    private static Path createSkillsJar(Path directory) throws IOException {
        Path sourceDir = Path.of("src/test/resources/skills");
        Path jarFile = directory.resolve("skills.jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile))) {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    String entryName = "skills/" + sourceDir.relativize(dir) + "/";
                    if (!entryName.equals("skills//")) {
                        jos.putNextEntry(new JarEntry(entryName));
                        jos.closeEntry();
                    } else {
                        jos.putNextEntry(new JarEntry("skills/"));
                        jos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String entryName = "skills/" + sourceDir.relativize(file);
                    jos.putNextEntry(new JarEntry(entryName));
                    Files.copy(file, jos);
                    jos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return jarFile;
    }
}
