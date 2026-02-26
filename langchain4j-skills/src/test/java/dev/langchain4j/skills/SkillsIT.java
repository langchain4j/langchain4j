package dev.langchain4j.skills;

import dev.langchain4j.LoggingChatModelListener;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_SONNET_4_6;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
public class SkillsIT {

    ChatModel model = AnthropicChatModel.builder()
            .apiKey(System.getenv("ANTHROPIC_API_KEY"))
            .modelName(CLAUDE_SONNET_4_6)
            .listeners(new LoggingChatModelListener())
            .build();

    interface Assistant {

        Result<String> chat(String userMessage);
    }

    /**
     * These tools have generic names, inconsistent arguments and cryptic return values on purpose,
     * they can "make sense" only when skill content/references is loaded.
     */
    class Tools {

        @Tool
        int process(String name, int id, String surname) {
            return 25;
        }

        @Tool
        int generateId(String surname, String name) {
            return 177;
        }

        @Tool
        void finish() {
        }

        @Tool
        void reset() {
        }
    }

    @Test
    void should_activate_skill_and_load_reference() { // TODO name

        // given
        Skill skill = FileSystemSkillLoader.loadSkill(toPath("skills/using-process-tool"));

        // when
        SkillService skillService = SkillService.from(skill);

        // then
        assertThat(skillService.systemMessage()).contains("using-process-tool");
        assertThat(getToolNames(skillService.toolProvider()))
                .containsExactlyInAnyOrder("activate_skill", "read_skill_resource");

        // given
        Tools spyTools = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .systemMessage(skillService.systemMessage())
                .tools(spyTools)
                .toolProvider(skillService.toolProvider())
                .build();

        // when
        assistant.chat("Use 'process' tool for Klaus Heisler");

        // then
        verify(spyTools).generateId("Heisler", "Klaus");
        verify(spyTools).process("Klaus", 177, "Heisler");
        verify(spyTools).reset();
        verifyNoMoreInteractions(spyTools);
    }

    @Test
    void should_activate_skill_and_load_reference__programmatic() { // TODO name

        // given
        Skill skill = Skill.builder()
                .name("using-process-tool")
                .description("Describes how to correctly use 'process' tool")
                .content("""
                        When user asks you to use the 'process' tool, you need to first call the 'generateId' tool with
                        2 arguments: arg0 (surname) and arg1 (name).
                        
                        When you have an id, call the 'process' tool with 3 arguments:
                        arg0 (name), arg1 (id), arg2 (surname).
                        
                        If 'process' tool returns code 17, proceed with [this](references/17.md) guide,
                        if it returns code 25, proceed with [this](references/25.md) guide.
                        """)
                .resources(List.of(
                        SkillResource.builder()
                                .relativePath("references/17.md")
                                .content("If 'process' tool returns code 17, you need to call the 'finish' tool.")
                                .build(),
                        SkillResource.builder()
                                .relativePath("references/25.md")
                                .content("If 'process' tool returns code 25, you need to call the 'reset' tool.")
                                .build()
                ))
                .build();

        // when
        SkillService skillService = SkillService.from(skill);

        // then
        assertThat(skillService.systemMessage()).contains("using-process-tool");
        assertThat(getToolNames(skillService.toolProvider()))
                .containsExactlyInAnyOrder("activate_skill", "read_skill_resource");

        // given
        Tools spyTools = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .systemMessage(skillService.systemMessage())
                .tools(spyTools)
                .toolProvider(skillService.toolProvider())
                .build();

        // when
        assistant.chat("Use 'process' tool for Klaus Heisler");

        // then
        verify(spyTools).generateId("Heisler", "Klaus");
        verify(spyTools).process("Klaus", 177, "Heisler");
        verify(spyTools).reset();
        verifyNoMoreInteractions(spyTools);
    }

    @Test
    void should_activate_skill_and_run_script() {

        // given
        Skill skill = FileSystemSkillLoader.loadSkill(toPath("skills/greeting-user"));

        // when
        SkillService skillService = SkillService.builder()
                .skills(skill)
                .allowRunningShellCommands(true)
                .build();

        // then
        assertThat(skillService.systemMessage()).contains("greeting-user");
        assertThat(getToolNames(skillService.toolProvider()))
                .containsExactlyInAnyOrder("activate_skill", "run_shell_command");

        // given
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .systemMessage(skillService.systemMessage())
                .toolProvider(skillService.toolProvider())
                .build();

        // when
        Result<String> result = assistant.chat("Greet the user");

        // then
        assertThat(result.content()).containsIgnoringCase("python from hello");
    }

    @Test
    void should_activate_docx_skill_and_run_scripts() { // TODO name

        // given
        Skill skill = FileSystemSkillLoader.loadSkill(toPath("skills/docx"));
        SkillService skillService = SkillService.builder()
                .skills(skill)
                .allowRunningShellCommands(true)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .systemMessage(skillService.systemMessage())
                .toolProvider(skillService.toolProvider())
                .build();

        // when
        Result<String> result = assistant.chat("Modify the word document C:\\dev\\output.docx in place, " +
                "change the color of the text to blue." +
                "Ignore all validation errors");

        // then
//        System.out.println(result.tokenUsage());
//        assertThat(response).containsIgnoringCase("hello from python");
    }

    @Test
    void should_activate_mcp_skill_and_run_scripts() { // TODO name

        // given
        Skill skill = FileSystemSkillLoader.loadSkill(toPath("skills/mcp-builder"));
        SkillService skillService = SkillService.builder()
                .skills(skill)
                .allowRunningShellCommands(true)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .systemMessage(skillService.systemMessage())
                .toolProvider(skillService.toolProvider())
                .build();

        // when
        Result<String> result = assistant.chat("Create a simple mcp server in python " +
                "with a single tool 'echo' that sends back whatever is sent to it");

        // then
//        System.out.println(result.tokenUsage());
//        assertThat(response).containsIgnoringCase("hello from python");
    }

    private Path toPath(String fileName) {
        try {
            return Paths.get(getClass().getClassLoader().getResource(fileName).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static Stream<String> getToolNames(ToolProvider toolProvider) {
        return toolProvider.provideTools(null).tools().keySet().stream().map(ToolSpecification::name);
    }
}
