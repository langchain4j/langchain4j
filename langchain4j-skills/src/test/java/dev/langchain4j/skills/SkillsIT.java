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
class SkillsIT {

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
        int generate(String surname, String name) {
            return 177;
        }

        @Tool
        void finish() {
        }

        @Tool
        void reset() {
        }

        @Tool
        String poll() {
            return "Klaus Heisler";
        }
    }

    @Test
    void should_activate_skill_and_load_resource() {

        // given
        Skill skill = FileSystemSkillLoader.loadSkill(toPath("skills/using-process-tool"));

        // when
        Skills skills = Skills.from(skill);

        // then
        assertThat(skills.formatAvailableSkills()).contains("using-process-tool");
        assertThat(getToolNames(skills.toolProvider()))
                .containsExactlyInAnyOrder("activate_skill", "read_skill_resource");
        assertThat(skills.toolProvider().provideTools(null).tools().keySet().stream()
                .filter(it -> it.name().equals("read_skill_resource")).findFirst().get()
                .parameters().properties().get("relative_path").description())
                .matches(".*For example: references/\\d+\\.md");

        // given
        Tools spyTools = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .systemMessage("You have access to the following skills: " + skills.formatAvailableSkills())
                .tools(spyTools)
                .toolProvider(skills.toolProvider())
                .build();

        // when
        assistant.chat("Use 'process' tool for Klaus Heisler");

        // then
        verify(spyTools).generate("Heisler", "Klaus");
        verify(spyTools).process("Klaus", 177, "Heisler");
        verify(spyTools).reset();
        verifyNoMoreInteractions(spyTools);
    }

    @Test
    void should_activate_skill_and_load_resource__programmatic() {

        // given
        Skill skill = Skill.builder()
                .name("using-process-tool")
                .description("Describes how to correctly use 'process' tool")
                .content("""
                        When user asks you to use the 'process' tool, you need to first call the 'generate' tool with
                        2 arguments: arg0 (surname) and arg1 (name).
                        
                        When you have an id, call the 'process' tool with 3 arguments:
                        arg0 (name), arg1 (id), arg2 (surname).
                        
                        If 'process' tool returns code 17, proceed with [this](references/17.md) guide,
                        if it returns code 25, proceed with [this](references/25.md) guide.
                        """)
                .resources(List.of(
                        SkillResource.builder()
                                .relativePath("references/17.md")
                                .content("If 'process' tool returns code 17, you need to call the 'finish' tool. " +
                                        "Do not call the 'reset' tool!")
                                .build(),
                        SkillResource.builder()
                                .relativePath("references/25.md")
                                .content("If 'process' tool returns code 25, you need to call the 'reset' tool. " +
                                        "Do not call the 'finish' tool!")
                                .build()
                ))
                .build();

        // when
        Skills skills = Skills.from(skill);

        // then
        assertThat(skills.formatAvailableSkills()).contains("using-process-tool");
        assertThat(getToolNames(skills.toolProvider()))
                .containsExactlyInAnyOrder("activate_skill", "read_skill_resource");

        // given
        Tools spyTools = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .systemMessage("You have access to the following skills: " + skills.formatAvailableSkills())
                .tools(spyTools)
                .toolProvider(skills.toolProvider())
                .build();

        // when
        assistant.chat("Use 'process' tool for Klaus Heisler");

        // then
        verify(spyTools).generate("Heisler", "Klaus");
        verify(spyTools).process("Klaus", 177, "Heisler");
        verify(spyTools).reset();
        verifyNoMoreInteractions(spyTools);
    }

    @Test
    void should_activate_multiple_skills() {

        // given
        Skill firstSkill = Skill.builder()
                .name("using-poll-tool")
                .description("Describes how to correctly use the 'poll' tool")
                .content("""
                        When user asks you to use the 'poll' tool, you need to call it and then call the 'process' tool
                        with the output of the 'poll' tool.
                        """)
                .build();

        Skill secondSkill = FileSystemSkillLoader.loadSkill(toPath("skills/using-process-tool"));

        // when
        Skills skills = Skills.from(firstSkill, secondSkill);

        // then
        assertThat(skills.formatAvailableSkills()).contains("using-poll-tool", "using-process-tool");
        assertThat(getToolNames(skills.toolProvider()))
                .containsExactlyInAnyOrder("activate_skill", "read_skill_resource");

        // given
        Tools spyTools = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .systemMessage("""
                        You have access to the following skills:
                        %s
                        When the user's request relates to one of these skills,
                        activate it first using the 'activate_skill' tool before proceeding.
                        """.formatted(skills.formatAvailableSkills()))
                .tools(spyTools)
                .toolProvider(skills.toolProvider())
                .build();

        // when
        assistant.chat("Use 'poll' tool");

        // then
        verify(spyTools).poll();
        verify(spyTools).generate("Heisler", "Klaus");
        verify(spyTools).process("Klaus", 177, "Heisler");
        verify(spyTools).reset();
        verifyNoMoreInteractions(spyTools);
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
