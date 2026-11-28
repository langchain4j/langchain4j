package dev.langchain4j.skills;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.io.TempDir;

class ClassPathSkillLoaderTest {

    private static final String SYMBOLIC_LINKS_NEED_PRIVILEGES_ON_WINDOWS =
            "creating symbolic links on Windows requires special privileges";
    private static final int SKILL_LOADING_THREAD_COUNT = 15;
    private static final int SKILL_LOADING_TIMEOUT_SECONDS = 30;

    @Test
    void should_load_all_skills_from_directory() {
        List<Skill> skills = ClassPathSkillLoader.loadSkills("skills");

        assertThat(skills)
                .extracting(Skill::name)
                .containsExactlyInAnyOrder("greeting-user", "test-skill", "using-process-tool");
    }

    @Test
    void should_skip_directories_without_skill_md() {
        List<Skill> skills = ClassPathSkillLoader.loadSkills("skills/using-process-tool");

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

            List<Skill> skills = ClassPathSkillLoader.loadSkills("skills", classLoader);

            assertThat(skills)
                    .extracting(Skill::name)
                    .containsExactlyInAnyOrder("greeting-user", "test-skill", "using-process-tool");
            assertThat(skills)
                    .filteredOn(skill -> skill.name().equals("greeting-user"))
                    .singleElement()
                    .satisfies(skill -> assertThat(skill.resources())
                            .extracting(SkillResource::relativePath)
                            .containsExactly("references/processing-result.md"));
        }
    }

    @Test
    void should_load_files_whose_names_start_with_scripts_outside_of_scripts_directory(@TempDir Path tempDir)
            throws IOException {
        Path skillDirectory = writeSkill(tempDir.resolve("skills/my-skill"));
        writeFile(skillDirectory.resolve("scripts/run.py"), "print('hello')");
        writeFile(skillDirectory.resolve("scripts-guide.md"), "guide");

        Skill skill = loadSkill(tempDir, "skills/my-skill");

        assertThat(skill.resources()).extracting(SkillResource::relativePath).containsExactly("scripts-guide.md");
    }

    @Test
    @DisabledOnOs(value = WINDOWS, disabledReason = SYMBOLIC_LINKS_NEED_PRIVILEGES_ON_WINDOWS)
    void should_load_symbolic_link_pointing_inside_of_skill_directory(@TempDir Path tempDir) throws IOException {
        Path skillDirectory = writeSkill(tempDir.resolve("skills/my-skill"));
        writeFile(skillDirectory.resolve("references/v2.md"), "version 2");
        Files.createSymbolicLink(skillDirectory.resolve("latest.md"), Path.of("references/v2.md"));

        Skill skill = loadSkill(tempDir, "skills/my-skill");

        assertThat(skill.resources())
                .extracting(SkillResource::relativePath, SkillResource::content)
                .containsExactlyInAnyOrder(tuple("latest.md", "version 2"), tuple("references/v2.md", "version 2"));
    }

    @Test
    @DisabledOnOs(value = WINDOWS, disabledReason = SYMBOLIC_LINKS_NEED_PRIVILEGES_ON_WINDOWS)
    void should_fail_when_resource_is_relative_symbolic_link_pointing_outside_of_skill_directory(
            @TempDir Path tempDir) throws IOException {
        writeFile(tempDir.resolve("outside.md"), "outside");
        Path skillDirectory = writeSkill(tempDir.resolve("skills/my-skill"));
        Files.createSymbolicLink(skillDirectory.resolve("notes.md"), Path.of("../../outside.md"));

        assertThatThrownBy(() -> loadSkill(tempDir, "skills/my-skill"))
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
        Path skillDirectory = writeSkill(tempDir.resolve("skills/my-skill"));
        Files.createDirectories(skillDirectory.resolve("references"));
        Files.createSymbolicLink(skillDirectory.resolve("references/notes.md"), outside.toAbsolutePath());

        assertThatThrownBy(() -> loadSkill(tempDir, "skills/my-skill"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("notes.md")
                .hasMessageContaining("outside of the skill directory")
                .hasMessageNotContaining("outside.md");
    }

    @Test
    @DisabledOnOs(value = WINDOWS, disabledReason = SYMBOLIC_LINKS_NEED_PRIVILEGES_ON_WINDOWS)
    void should_fail_when_SKILL_md_is_symbolic_link_pointing_outside_of_skill_directory(@TempDir Path tempDir)
            throws IOException {
        writeSkill(tempDir.resolve("skills/my-skill2")); // shares the "my-skill" name prefix, but is a different directory
        Path skillDirectory = Files.createDirectories(tempDir.resolve("skills/my-skill"));
        Files.createSymbolicLink(skillDirectory.resolve("SKILL.md"), Path.of("../my-skill2/SKILL.md"));

        assertThatThrownBy(() -> loadSkill(tempDir, "skills/my-skill"))
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

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null)) {

            assertThatThrownBy(() -> ClassPathSkillLoader.loadSkills("skills", classLoader))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("notes.md")
                    .hasMessageContaining("outside of the skill directory");
        }
    }

    @Test
    @DisabledOnOs(value = WINDOWS, disabledReason = SYMBOLIC_LINKS_NEED_PRIVILEGES_ON_WINDOWS)
    void should_not_follow_symbolic_link_to_directory_outside_of_skill_directory(@TempDir Path tempDir)
            throws IOException {
        writeFile(tempDir.resolve("outside/ref.md"), "outside");
        Path skillDirectory = writeSkill(tempDir.resolve("skills/my-skill"));
        Files.createSymbolicLink(skillDirectory.resolve("references"), Path.of("../../outside"));

        Skill skill = loadSkill(tempDir, "skills/my-skill");

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

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null)) {

            List<Skill> skills = ClassPathSkillLoader.loadSkills("skills", classLoader);

            assertThat(skills).singleElement().isInstanceOfSatisfying(FileSystemSkill.class, skill -> {
                assertThat(skill.basePath()).isEqualTo(skillDirectory);
                assertThat(skill.resources())
                        .extracting(SkillResource::relativePath, SkillResource::content)
                        .containsExactly(tuple("references/ref.md", "reference"));
            });
        }
    }

    @Test
    void should_load_skills_in_parallel_from_directory(@TempDir Path tempDir) throws Exception {
        int skillsCount = 20;
        String skillDirectoryPathPrefix = "skills/skill-";
        for (int skillIndex = 0; skillIndex < skillsCount; skillIndex++) {
            writeSkill(tempDir.resolve(skillDirectoryPathPrefix + skillIndex));
        }

        ExecutorService executor = Executors.newFixedThreadPool(SKILL_LOADING_THREAD_COUNT);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null)) {
            List<Callable<Skill>> loadSkillOperations = new ArrayList<>();
            for (int skillIndex = 0; skillIndex < skillsCount; skillIndex++) {
                String skillDirectory = skillDirectoryPathPrefix + skillIndex;
                loadSkillOperations.add(() -> ClassPathSkillLoader.loadSkill(skillDirectory, classLoader));
            }

            List<String> names = new ArrayList<>();
            for (Future<Skill> pendingSkill : executor.invokeAll(loadSkillOperations)) {
                names.add(pendingSkill.get(SKILL_LOADING_TIMEOUT_SECONDS, SECONDS).name());
            }
            assertThat(names).hasSize(skillsCount).doesNotHaveDuplicates();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void should_load_skills_in_parallel_from_jar(@TempDir Path tempDir) throws Exception {
        Path jarFile = createSkillsJar(tempDir);
        List<String> skillNames = List.of("greeting-user", "test-skill", "using-process-tool");
        int operationsPerSkill = 30;

        ExecutorService executor = Executors.newFixedThreadPool(SKILL_LOADING_THREAD_COUNT);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {jarFile.toUri().toURL()}, null)) {
            // all operations wait at the latch, so they open and close the JAR filesystem at the same time
            CountDownLatch startSignal = new CountDownLatch(1);
            List<Callable<Skill>> loadSkillOperations = new ArrayList<>();
            for (int i = 0; i < operationsPerSkill; i++) {
                for (String skillName : skillNames) {
                    loadSkillOperations.add(() -> {
                        startSignal.await();
                        return ClassPathSkillLoader.loadSkill("skills/" + skillName, classLoader);
                    });
                }
            }

            List<Future<Skill>> pendingSkills = new ArrayList<>();
            for (Callable<Skill> operation : loadSkillOperations) {
                pendingSkills.add(executor.submit(operation));
            }
            startSignal.countDown();

            List<String> names = new ArrayList<>();
            for (Future<Skill> pendingSkill : pendingSkills) {
                // rethrows an operation failure as ExecutionException
                names.add(pendingSkill.get(SKILL_LOADING_TIMEOUT_SECONDS, SECONDS).name());
            }
            assertThat(names).hasSize(skillNames.size() * operationsPerSkill);
            assertThat(names.stream().distinct().sorted().toList()).isEqualTo(skillNames);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void should_load_skills_from_jar_whose_path_contains_spaces(@TempDir Path tempDir) throws IOException {
        // the JAR URI is then URL-encoded: jar:file:/.../dir%20with%20spaces/skills.jar!/skills/...
        Path jarFile = createSkillsJar(Files.createDirectories(tempDir.resolve("dir with spaces")));
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {jarFile.toUri().toURL()}, null)) {

            Skill skill = ClassPathSkillLoader.loadSkill("skills/using-process-tool", classLoader);
            List<Skill> skills = ClassPathSkillLoader.loadSkills("skills", classLoader);

            assertThat(skill.name()).isEqualTo("using-process-tool");
            assertThat(skill.resources())
                    .extracting(SkillResource::relativePath)
                    .containsExactlyInAnyOrder("references/17.md", "references/25.md");
            assertThat(skills)
                    .extracting(Skill::name)
                    .containsExactlyInAnyOrder("greeting-user", "test-skill", "using-process-tool");
        }
    }

    private static Skill loadSkill(Path classpathRoot, String skillDirectoryOnClasspath) throws IOException {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {classpathRoot.toUri().toURL()}, null)) {
            return ClassPathSkillLoader.loadSkill(skillDirectoryOnClasspath, classLoader);
        }
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
                        jos.putNextEntry(new JarEntry(entryName.replaceAll("\\\\", "/")));
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
                    jos.putNextEntry(new JarEntry(entryName.replaceAll("\\\\", "/")));
                    Files.copy(file, jos);
                    jos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return jarFile;
    }
}
