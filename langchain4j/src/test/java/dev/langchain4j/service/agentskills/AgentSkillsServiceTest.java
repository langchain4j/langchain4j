package dev.langchain4j.service.agentskills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentskills.AgentSkillsProvider;
import dev.langchain4j.agentskills.AgentSkillsProviderResult;
import dev.langchain4j.agentskills.Skill;
import dev.langchain4j.agentskills.execution.ScriptExecutionResult;
import dev.langchain4j.agentskills.execution.ScriptExecutor;
import dev.langchain4j.agentskills.instruction.AgentSkillsInstruction;
import dev.langchain4j.agentskills.instruction.ExecuteScriptInstruction;
import dev.langchain4j.agentskills.instruction.ReadResourceInstruction;
import dev.langchain4j.agentskills.instruction.UseSkillInstruction;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link AgentSkillsService}.
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 */
class AgentSkillsServiceTest {

    @Test
    void should_parse_use_skill_instruction() {
        // given
        String message = "Some text <use_skill>pdf-processing</use_skill> more text";
        AgentSkillsService service = new AgentSkillsService();

        // when
        AgentSkillsInstruction instruction = service.parseInstruction(message);

        // then
        assertThat(instruction).isInstanceOf(UseSkillInstruction.class);
        assertThat(((UseSkillInstruction) instruction).skillName()).isEqualTo("pdf-processing");
    }

    @Test
    void should_parse_execute_script_instruction() {
        // given
        String message = "<execute_script skill=\"pdf-processing\">scripts/extract.py file.pdf</execute_script>";
        AgentSkillsService service = new AgentSkillsService();

        // when
        AgentSkillsInstruction instruction = service.parseInstruction(message);

        // then
        assertThat(instruction).isInstanceOf(ExecuteScriptInstruction.class);
        ExecuteScriptInstruction exec = (ExecuteScriptInstruction) instruction;
        assertThat(exec.skillName()).isEqualTo("pdf-processing");
        assertThat(exec.command()).isEqualTo("scripts/extract.py file.pdf");
    }

    @Test
    void should_parse_read_resource_instruction() {
        // given
        String message = "<read_resource skill=\"pdf-processing\">assets/template.json</read_resource>";
        AgentSkillsService service = new AgentSkillsService();

        // when
        AgentSkillsInstruction instruction = service.parseInstruction(message);

        // then
        assertThat(instruction).isInstanceOf(ReadResourceInstruction.class);
        ReadResourceInstruction read = (ReadResourceInstruction) instruction;
        assertThat(read.skillName()).isEqualTo("pdf-processing");
        assertThat(read.resourcePath()).isEqualTo("assets/template.json");
    }

    @Test
    void should_return_null_when_no_instruction_found() {
        // given
        String message = "Just a normal message without any instructions";
        AgentSkillsService service = new AgentSkillsService();

        // when
        AgentSkillsInstruction instruction = service.parseInstruction(message);

        // then
        assertThat(instruction).isNull();
    }

    @Test
    void should_parse_only_first_instruction() {
        // given
        String message = "<use_skill>skill1</use_skill> text <use_skill>skill2</use_skill>";
        AgentSkillsService service = new AgentSkillsService();

        // when
        AgentSkillsInstruction instruction = service.parseInstruction(message);

        // then
        assertThat(instruction).isInstanceOf(UseSkillInstruction.class);
        assertThat(((UseSkillInstruction) instruction).skillName()).isEqualTo("skill1");
    }

    @Test
    void should_return_error_when_skill_not_found_for_use_skill() {
        // given
        AgentSkillsProvider provider = mock(AgentSkillsProvider.class);
        when(provider.skillByName("non-existent")).thenReturn(null);

        AgentSkillsService service = new AgentSkillsService();
        service.agentSkillsProvider(provider);

        // when
        String result = service.handleUseSkill("non-existent");

        // then
        assertThat(result).contains("<skill_error>");
        assertThat(result).contains("non-existent");
    }

    @Test
    void should_return_error_when_skill_not_found_for_execute_script() {
        // given
        AgentSkillsProvider provider = mock(AgentSkillsProvider.class);
        when(provider.skillByName("non-existent")).thenReturn(null);

        AgentSkillsService service = new AgentSkillsService();
        service.agentSkillsProvider(provider);

        // when
        String result = service.handleExecuteScript("non-existent", "scripts/test.sh");

        // then
        assertThat(result).contains("<script_error>");
        assertThat(result).contains("non-existent");
    }

    @Test
    void should_return_error_when_skill_not_found_for_read_resource() {
        // given
        AgentSkillsProvider provider = mock(AgentSkillsProvider.class);
        when(provider.skillByName("non-existent")).thenReturn(null);

        AgentSkillsService service = new AgentSkillsService();
        service.agentSkillsProvider(provider);

        // when
        String result = service.handleReadResource("non-existent", "assets/file.txt");

        // then
        assertThat(result).contains("<resource_error>");
    }

    @Test
    void should_reject_command_not_in_allowed_tools(@TempDir Path tempDir) throws IOException {
        // given
        Skill skill = createSkillWithAllowedTools(tempDir, "pdf-processing", "python", "node");
        AgentSkillsProvider provider = mock(AgentSkillsProvider.class);
        when(provider.skillByName("pdf-processing")).thenReturn(skill);

        ScriptExecutor executor = mock(ScriptExecutor.class);
        AgentSkillsService service = new AgentSkillsService();
        service.agentSkillsProvider(provider);
        service.scriptExecutor(executor);

        // when
        String result = service.handleExecuteScript("pdf-processing", "bash malicious.sh");

        // then
        assertThat(result).contains("<script_error>");
        assertThat(result.toLowerCase()).containsAnyOf("not in", "allowed");
        verify(executor, never()).execute(any(), any());
    }

    @Test
    void should_allow_command_in_allowed_tools(@TempDir Path tempDir) throws IOException {
        // given
        Skill skill = createSkillWithAllowedTools(tempDir, "pdf-processing", "python", "bash");
        AgentSkillsProvider provider = mock(AgentSkillsProvider.class);
        when(provider.skillByName("pdf-processing")).thenReturn(skill);

        ScriptExecutor executor = mock(ScriptExecutor.class);
        ScriptExecutionResult execResult = ScriptExecutionResult.builder()
                .exitCode(0)
                .output("OK")
                .error("")
                .build();
        when(executor.execute(any(Path.class), eq("python scripts/test.py"))).thenReturn(execResult);

        AgentSkillsService service = new AgentSkillsService();
        service.agentSkillsProvider(provider);
        service.scriptExecutor(executor);

        // when
        String result = service.handleExecuteScript("pdf-processing", "python scripts/test.py");

        // then
        assertThat(result).contains("<script_result exit_code=\"0\">");
        verify(executor, times(1)).execute(any(), eq("python scripts/test.py"));
    }

    @Test
    void should_use_default_script_executor_when_not_configured() {
        // given
        AgentSkillsService service = new AgentSkillsService();

        // when
        ScriptExecutor executor = service.scriptExecutor();

        // then
        assertThat(executor).isNotNull();
        assertThat(executor).isInstanceOf(dev.langchain4j.agentskills.execution.DefaultScriptExecutor.class);
    }

    @Test
    void should_return_error_when_resource_not_found(@TempDir Path tempDir) throws IOException {
        // given
        Skill skill = createSkill(tempDir, "pdf-processing", "Process PDFs");
        AgentSkillsProvider provider = mock(AgentSkillsProvider.class);
        when(provider.skillByName("pdf-processing")).thenReturn(skill);

        AgentSkillsService service = new AgentSkillsService();
        service.agentSkillsProvider(provider);

        // when
        String result = service.handleReadResource("pdf-processing", "assets/non-existent.json");

        // then
        assertThat(result).contains("<resource_error>");
    }

    @Test
    void should_prevent_path_traversal_attack(@TempDir Path tempDir) throws IOException {
        // given
        Skill skill = createSkill(tempDir, "pdf-processing", "Process PDFs");
        AgentSkillsProvider provider = mock(AgentSkillsProvider.class);
        when(provider.skillByName("pdf-processing")).thenReturn(skill);

        AgentSkillsService service = new AgentSkillsService();
        service.agentSkillsProvider(provider);

        // when
        String result = service.handleReadResource("pdf-processing", "../../../etc/passwd");

        // then
        assertThat(result).contains("<resource_error>");
        assertThat(result.toLowerCase()).containsAnyOf("illegal", "path");
    }

    @Test
    void should_generate_system_prompt_addition(@TempDir Path tempDir) throws IOException {
        // given
        Skill skill1 = createSkill(tempDir.resolve("skill1"), "pdf-processing", "Process PDFs");
        Skill skill2 = createSkill(tempDir.resolve("skill2"), "web-search", "Search web");

        AgentSkillsProvider provider = mock(AgentSkillsProvider.class);
        AgentSkillsProviderResult providerResult = AgentSkillsProviderResult.builder()
                .skills(Arrays.asList(skill1, skill2))
                .build();
        when(provider.provideSkills(any())).thenReturn(providerResult);

        AgentSkillsService service = new AgentSkillsService();
        service.agentSkillsProvider(provider);

        InvocationContext context = mock(InvocationContext.class);
        UserMessage userMessage = UserMessage.from("test");

        // when
        String addition = service.generateSystemPromptAddition(context, userMessage);

        // then
        assertThat(addition).contains("<available_skills>");
        assertThat(addition).contains("pdf-processing");
        assertThat(addition).contains("Process PDFs");
        assertThat(addition).contains("web-search");
        assertThat(addition).contains("Search web");
        assertThat(addition).contains("use_skill");
        assertThat(addition).contains("execute_script");
        assertThat(addition).contains("read_resource");
    }

    @Test
    void should_return_empty_string_for_empty_skills() {
        // given
        AgentSkillsProvider provider = mock(AgentSkillsProvider.class);
        AgentSkillsProviderResult emptyResult =
                AgentSkillsProviderResult.builder().skills(Collections.emptyList()).build();
        when(provider.provideSkills(any())).thenReturn(emptyResult);

        AgentSkillsService service = new AgentSkillsService();
        service.agentSkillsProvider(provider);

        InvocationContext context = mock(InvocationContext.class);
        UserMessage userMessage = UserMessage.from("test");

        // when
        String addition = service.generateSystemPromptAddition(context, userMessage);

        // then
        assertThat(addition).isEmpty();
    }

    @Test
    void should_return_false_when_no_provider() {
        // given
        AgentSkillsService service = new AgentSkillsService();

        // when-then
        assertThat(service.hasSkills()).isFalse();
    }

    @Test
    void should_return_true_when_has_skills() {
        // given
        AgentSkillsProvider provider = mock(AgentSkillsProvider.class);

        AgentSkillsService service = new AgentSkillsService();
        service.agentSkillsProvider(provider);

        // when-then
        assertThat(service.hasSkills()).isTrue();
    }

    @Test
    void should_throw_exception_when_max_iterations_less_than_one() {
        // given
        AgentSkillsService service = new AgentSkillsService();

        // when-then
        assertThatThrownBy(() -> service.maxIterations(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxIterations");

        assertThatThrownBy(() -> service.maxIterations(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxIterations");
    }

    @Test
    void should_accept_valid_max_iterations() {
        // given
        AgentSkillsService service = new AgentSkillsService();

        // when
        service.maxIterations(1);
        service.maxIterations(10);
        service.maxIterations(100);

        // then
        assertThat(service.maxIterations()).isEqualTo(100);
    }

    @Test
    void should_handle_wildcard_in_allowed_tools(@TempDir Path tempDir) throws IOException {
        // given
        Skill skill = createSkillWithAllowedTools(tempDir, "test-skill", "*");
        AgentSkillsProvider provider = mock(AgentSkillsProvider.class);
        when(provider.skillByName("test-skill")).thenReturn(skill);

        ScriptExecutor executor = mock(ScriptExecutor.class);
        ScriptExecutionResult execResult =
                ScriptExecutionResult.builder().exitCode(0).output("OK").error("").build();
        when(executor.execute(any(Path.class), any(String.class))).thenReturn(execResult);

        AgentSkillsService service = new AgentSkillsService();
        service.agentSkillsProvider(provider);
        service.scriptExecutor(executor);

        // when
        String result = service.handleExecuteScript("test-skill", "any-command-here");

        // then: wildcard allows any command
        assertThat(result).contains("<script_result exit_code=\"0\">");
        verify(executor, times(1)).execute(any(), eq("any-command-here"));
    }

    @Test
    void should_read_resource_successfully(@TempDir Path tempDir) throws IOException {
        // given
        Skill skill = createSkill(tempDir, "pdf-processing", "Process PDFs");
        Path assetsDir = skill.path().resolve("assets");
        Files.createDirectories(assetsDir);
        Files.writeString(assetsDir.resolve("template.json"), "{\"key\": \"value\"}");

        AgentSkillsProvider provider = mock(AgentSkillsProvider.class);
        when(provider.skillByName("pdf-processing")).thenReturn(skill);

        AgentSkillsService service = new AgentSkillsService();
        service.agentSkillsProvider(provider);

        // when
        String result = service.handleReadResource("pdf-processing", "assets/template.json");

        // then
        assertThat(result).contains("<resource_content");
        assertThat(result).contains("\"key\": \"value\"");
    }

    @Test
    void should_return_skill_content_for_use_skill(@TempDir Path tempDir) throws IOException {
        // given
        Skill skill = createSkillWithInstructions(
                tempDir, "pdf-processing", "Process PDFs", "# PDF Skill\n\nExtract text from PDFs.");
        AgentSkillsProvider provider = mock(AgentSkillsProvider.class);
        when(provider.skillByName("pdf-processing")).thenReturn(skill);

        AgentSkillsService service = new AgentSkillsService();
        service.agentSkillsProvider(provider);

        // when
        String result = service.handleUseSkill("pdf-processing");

        // then
        assertThat(result).contains("<skill_content name=\"pdf-processing\">");
        assertThat(result).contains("# PDF Skill");
        assertThat(result).contains("Extract text from PDFs");
    }

    @Test
    void should_handle_script_execution_with_non_zero_exit_code(@TempDir Path tempDir) throws IOException {
        // given
        Skill skill = createSkill(tempDir, "test-skill", "Test");
        AgentSkillsProvider provider = mock(AgentSkillsProvider.class);
        when(provider.skillByName("test-skill")).thenReturn(skill);

        ScriptExecutor executor = mock(ScriptExecutor.class);
        ScriptExecutionResult failedResult = ScriptExecutionResult.builder()
                .exitCode(1)
                .output("")
                .error("Script failed")
                .build();
        when(executor.execute(any(), any())).thenReturn(failedResult);

        AgentSkillsService service = new AgentSkillsService();
        service.agentSkillsProvider(provider);
        service.scriptExecutor(executor);

        // when
        String result = service.handleExecuteScript("test-skill", "failing-command");

        // then
        assertThat(result).contains("<script_result exit_code=\"1\">");
        assertThat(result).contains("Script failed");
    }

    @Test
    void should_return_error_when_command_is_blank() {
        // given
        AgentSkillsProvider provider = mock(AgentSkillsProvider.class);
        AgentSkillsService service = new AgentSkillsService();
        service.agentSkillsProvider(provider);

        // when
        String result = service.handleExecuteScript("any-skill", "   ");

        // then
        assertThat(result).contains("<script_error>");
        assertThat(result.toLowerCase()).containsAnyOf("cannot be empty", "empty");
    }

    @Test
    void should_return_error_when_resource_path_is_blank() {
        // given
        AgentSkillsProvider provider = mock(AgentSkillsProvider.class);
        AgentSkillsService service = new AgentSkillsService();
        service.agentSkillsProvider(provider);

        // when
        String result = service.handleReadResource("any-skill", "   ");

        // then
        assertThat(result).contains("<resource_error>");
        assertThat(result.toLowerCase()).containsAnyOf("cannot be empty", "empty");
    }

    @Test
    void should_return_empty_string_when_provider_is_null() {
        // given
        AgentSkillsService service = new AgentSkillsService();
        // no provider set

        InvocationContext context = mock(InvocationContext.class);
        UserMessage userMessage = UserMessage.from("test");

        // when
        String addition = service.generateSystemPromptAddition(context, userMessage);

        // then
        assertThat(addition).isEmpty();
    }

    // Helper methods

    private Skill createSkill(Path parentDir, String skillName, String description) throws IOException {
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

        return Skill.builder()
                .name(skillName)
                .description(description)
                .path(skillDir)
                .instructions("# " + skillName)
                .build();
    }

    private Skill createSkillWithInstructions(Path parentDir, String skillName, String description, String instructions)
            throws IOException {
        Path skillDir = parentDir.resolve(skillName);
        Files.createDirectories(skillDir);

        return Skill.builder()
                .name(skillName)
                .description(description)
                .path(skillDir)
                .instructions(instructions)
                .build();
    }

    private Skill createSkillWithAllowedTools(Path parentDir, String skillName, String... allowedTools)
            throws IOException {
        Path skillDir = parentDir.resolve(skillName);
        Files.createDirectories(skillDir);

        String allowedToolsStr = String.join(" ", allowedTools);
        String skillMd = String.format(
                """
                ---
                name: %s
                description: Test skill
                allowed-tools: %s
                ---
                # %s
                """,
                skillName, allowedToolsStr, skillName);

        Files.writeString(skillDir.resolve("SKILL.md"), skillMd);

        return Skill.builder()
                .name(skillName)
                .description("Test skill")
                .path(skillDir)
                .allowedTools(Arrays.asList(allowedTools))
                .instructions("# " + skillName)
                .build();
    }
}
