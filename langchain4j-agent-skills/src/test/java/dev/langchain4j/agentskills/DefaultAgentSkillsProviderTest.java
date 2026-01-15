package dev.langchain4j.agentskills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link DefaultAgentSkillsProvider}.
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 */
class DefaultAgentSkillsProviderTest {

    @Test
    void should_load_skills_from_single_directory(@TempDir Path tempDir) throws IOException {
        // given
        createSkill(tempDir, "pdf-processing", "Process PDFs");
        createSkill(tempDir, "web-search", "Search the web");

        // when
        DefaultAgentSkillsProvider provider =
                DefaultAgentSkillsProvider.builder().skillDirectories(tempDir).build();

        // then
        List<Skill> skills = provider.allSkills();
        assertThat(skills).hasSize(2);
        assertThat(skills).extracting(Skill::name).containsExactlyInAnyOrder("pdf-processing", "web-search");
    }

    @Test
    void should_load_skills_from_multiple_directories(@TempDir Path tempDir) throws IOException {
        // given
        Path dir1 = tempDir.resolve("dir1");
        Path dir2 = tempDir.resolve("dir2");
        Files.createDirectories(dir1);
        Files.createDirectories(dir2);

        createSkill(dir1, "skill1", "Skill 1");
        createSkill(dir2, "skill2", "Skill 2");

        // when
        DefaultAgentSkillsProvider provider =
                DefaultAgentSkillsProvider.builder().skillDirectories(dir1, dir2).build();

        // then
        List<Skill> skills = provider.allSkills();
        assertThat(skills).hasSize(2);
        assertThat(skills).extracting(Skill::name).containsExactlyInAnyOrder("skill1", "skill2");
    }

    @Test
    void should_find_skill_by_name(@TempDir Path tempDir) throws IOException {
        // given
        createSkill(tempDir, "pdf-processing", "Process PDFs");
        createSkill(tempDir, "web-search", "Search the web");

        DefaultAgentSkillsProvider provider =
                DefaultAgentSkillsProvider.builder().skillDirectories(tempDir).build();

        // when
        Skill skill = provider.skillByName("pdf-processing");

        // then
        assertThat(skill).isNotNull();
        assertThat(skill.name()).isEqualTo("pdf-processing");
        assertThat(skill.description()).isEqualTo("Process PDFs");
    }

    @Test
    void should_return_null_when_skill_not_found(@TempDir Path tempDir) throws IOException {
        // given
        createSkill(tempDir, "existing-skill", "Exists");

        DefaultAgentSkillsProvider provider =
                DefaultAgentSkillsProvider.builder().skillDirectories(tempDir).build();

        // when
        Skill skill = provider.skillByName("non-existent");

        // then
        assertThat(skill).isNull();
    }

    @Test
    void should_provide_skills_through_provider_interface(@TempDir Path tempDir) throws IOException {
        // given
        createSkill(tempDir, "test-skill", "Test");

        DefaultAgentSkillsProvider provider =
                DefaultAgentSkillsProvider.builder().skillDirectories(tempDir).build();

        AgentSkillsProviderRequest request =
                AgentSkillsProviderRequest.builder().invocationContext(null).userMessage(null).build();

        // when
        AgentSkillsProviderResult result = provider.provideSkills(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.skills()).hasSize(1);
        assertThat(result.hasSkills()).isTrue();
    }

    @Test
    void should_cache_loaded_skills(@TempDir Path tempDir) throws IOException {
        // given
        createSkill(tempDir, "test-skill", "Test");

        DefaultAgentSkillsProvider provider =
                DefaultAgentSkillsProvider.builder().skillDirectories(tempDir).build();

        // when
        List<Skill> skills1 = provider.allSkills();
        List<Skill> skills2 = provider.allSkills();

        // then: both calls return the same cached data
        assertThat(skills1).hasSize(1);
        assertThat(skills2).hasSize(1);
        assertThat(skills1.get(0).name()).isEqualTo(skills2.get(0).name());
    }

    @Test
    void should_reload_skills_after_reload_call(@TempDir Path tempDir) throws IOException {
        // given
        createSkill(tempDir, "skill1", "Skill 1");

        DefaultAgentSkillsProvider provider =
                DefaultAgentSkillsProvider.builder().skillDirectories(tempDir).build();

        List<Skill> skillsBefore = provider.allSkills();
        assertThat(skillsBefore).hasSize(1);

        // when: add new skill and reload
        createSkill(tempDir, "skill2", "Skill 2");
        provider.reload();
        List<Skill> skillsAfter = provider.allSkills();

        // then
        assertThat(skillsAfter).hasSize(2);
        assertThat(skillsAfter).extracting(Skill::name).containsExactlyInAnyOrder("skill1", "skill2");
    }

    @Test
    void should_handle_duplicate_skill_names_across_directories(@TempDir Path tempDir) throws IOException {
        // given
        Path dir1 = tempDir.resolve("dir1");
        Path dir2 = tempDir.resolve("dir2");
        Files.createDirectories(dir1);
        Files.createDirectories(dir2);

        createSkill(dir1, "pdf-processing", "Description from dir1");
        createSkill(dir2, "pdf-processing", "Description from dir2");

        // when
        DefaultAgentSkillsProvider provider =
                DefaultAgentSkillsProvider.builder().skillDirectories(dir1, dir2).build();

        // then: later directory skips duplicate names
        List<Skill> skills = provider.allSkills();
        assertThat(skills).hasSize(1);
        assertThat(skills.get(0).name()).isEqualTo("pdf-processing");
        // First one is kept, second is skipped with warning
        assertThat(skills.get(0).description()).isEqualTo("Description from dir1");
    }

    @Test
    void should_return_empty_list_for_empty_directory(@TempDir Path tempDir) {
        // given
        DefaultAgentSkillsProvider provider =
                DefaultAgentSkillsProvider.builder().skillDirectories(tempDir).build();

        // when
        List<Skill> skills = provider.allSkills();

        // then
        assertThat(skills).isEmpty();
    }

    @Test
    void should_handle_non_existent_directory(@TempDir Path tempDir) {
        // given
        Path nonExistent = tempDir.resolve("non-existent");

        // when
        DefaultAgentSkillsProvider provider =
                DefaultAgentSkillsProvider.builder().skillDirectories(nonExistent).build();

        // then: should not throw exception, just return empty list
        List<Skill> skills = provider.allSkills();
        assertThat(skills).isEmpty();
    }

    @Test
    void should_handle_concurrent_access(@TempDir Path tempDir) throws Exception {
        // given
        createSkill(tempDir, "skill1", "Skill 1");
        createSkill(tempDir, "skill2", "Skill 2");

        DefaultAgentSkillsProvider provider =
                DefaultAgentSkillsProvider.builder().skillDirectories(tempDir).build();

        // when: 10 threads access concurrently
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    List<Skill> skills = provider.allSkills();
                    if (skills.size() == 2) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // then: all threads should succeed
        assertThat(successCount.get()).isEqualTo(threadCount);
    }

    @Test
    void should_throw_exception_when_skill_directories_is_empty() {
        // when-then
        assertThatThrownBy(() -> DefaultAgentSkillsProvider.builder().skillDirectories().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skillDirectories");
    }

    @Test
    void should_throw_exception_when_add_null_directory() {
        // when-then
        assertThatThrownBy(() -> DefaultAgentSkillsProvider.builder().addSkillDirectory(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("directory");
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
