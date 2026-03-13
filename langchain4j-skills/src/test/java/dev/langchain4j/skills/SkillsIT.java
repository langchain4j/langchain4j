package dev.langchain4j.skills;

import dev.langchain4j.LoggingChatModelListener;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_SONNET_4_6;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.inOrder;
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
        assertThat(getToolNames(skills.toolProviders()))
                .containsExactlyInAnyOrder("activate_skill", "read_skill_resource");
        assertThat(skills.toolProviders().get(0).provideTools(null).tools().keySet().stream()
                .filter(it -> it.name().equals("read_skill_resource")).findFirst().get()
                .parameters().properties().get("relative_path").description())
                .matches(".*For example: references/\\d+\\.md");

        // given
        Tools spyTools = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .systemMessage("You have access to the following skills: " + skills.formatAvailableSkills())
                .tools(spyTools)
                .toolProviders(skills.toolProviders())
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
        assertThat(getToolNames(skills.toolProviders()))
                .containsExactlyInAnyOrder("activate_skill", "read_skill_resource");

        // given
        Tools spyTools = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .systemMessage("You have access to the following skills: " + skills.formatAvailableSkills())
                .tools(spyTools)
                .toolProviders(skills.toolProviders())
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
        assertThat(getToolNames(skills.toolProviders()))
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
                .toolProviders(skills.toolProviders())
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

    @Test
    void should_not_include_skill_tools_before_activation_and_include_after() {

        // given
        ToolSpecification skillTool = ToolSpecification.builder()
                .name("query_inventory")
                .description("Queries the internal inventory system for stock levels")
                .build();

        ToolProvider skillToolProvider = request -> ToolProviderResult.builder()
                .add(skillTool, (req, memoryId) -> "47 units in stock")
                .build();

        Skill skill = Skill.builder()
                .name("inventory-management")
                .description("Describes how to query and manage the internal inventory system")
                .content("When asked about inventory or stock levels, use the 'query_inventory' tool.")
                .toolProviders(skillToolProvider)
                .build();

        Skills skills = Skills.from(skill);

        ChatModel spyChatModel = spy(model);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .systemMessage("""
                        You have access to the following skills:
                        %s
                        When the user's request relates to one of these skills,
                        activate it first using the 'activate_skill' tool before proceeding.
                        """.formatted(skills.formatAvailableSkills()))
                .toolProviders(skills.toolProviders())
                .build();

        // when — first invocation: skill is not yet activated
        assistant.chat("Check the inventory for widgets");

        // then
        InOrder inOrder = inOrder(spyChatModel);

        // First LLM call: only activate_skill tool should be present, no query_inventory
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && !containsTool(request, "query_inventory")
        ));

        // Second LLM call: skill was activated (messages contain activate_skill call),
        // so query_inventory should now appear
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "query_inventory")
                        && request.messages().stream().anyMatch(msg -> // TODO
                                msg instanceof AiMessage ai
                                        && ai.hasToolExecutionRequests()
                                        && ai.toolExecutionRequests().stream().anyMatch(ter ->
                                                ter.name().equals("activate_skill")
                                                        && ter.arguments().contains("inventory-management")))
        ));

        // when — second invocation: skill was already activated in chat memory,
        // so query_inventory should be present from the very first LLM call
        assistant.chat("Now check the inventory for gadgets");

        // then — query_inventory should be present on the first LLM call of the second invocation
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "query_inventory")
        ));
    }

    @Test
    void should_not_include_skill_tools_for_unrelated_skill() {

        // given
        ToolSpecification weatherTool = ToolSpecification.builder()
                .name("get_weather")
                .description("Gets the weather")
                .build();

        ToolSpecification timeTool = ToolSpecification.builder()
                .name("get_time")
                .description("Gets the current time")
                .build();

        Skill weatherSkill = Skill.builder()
                .name("weather")
                .description("A weather skill")
                .content("When asked about weather, use the 'get_weather' tool.")
                .toolProviders(request -> ToolProviderResult.builder()
                        .add(weatherTool, (req, memoryId) -> "Sunny")
                        .build())
                .build();

        Skill timeSkill = Skill.builder()
                .name("time")
                .description("A time skill")
                .content("When asked about time, use the 'get_time' tool.")
                .toolProviders(request -> ToolProviderResult.builder()
                        .add(timeTool, (req, memoryId) -> "12:00")
                        .build())
                .build();

        Skills skills = Skills.from(weatherSkill, timeSkill);

        ChatModel spyChatModel = spy(model);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .systemMessage("""
                        You have access to the following skills:
                        %s
                        When the user's request relates to one of these skills,
                        activate it first using the 'activate_skill' tool before proceeding.
                        Activate only the relevant skill. Do NOT activate unrelated skills.
                        """.formatted(skills.formatAvailableSkills()))
                .toolProviders(skills.toolProviders())
                .build();

        // when
        Result<String> result = assistant.chat("What is the weather?");

        // then
        assertThat(result.content().toLowerCase()).contains("sunny");

        InOrder inOrder = inOrder(spyChatModel);

        // First call: only activate_skill, no skill-specific tools
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && !containsTool(request, "get_weather")
                        && !containsTool(request, "get_time")
        ));

        // After activating weather skill: get_weather should appear, get_time should NOT
        inOrder.verify(spyChatModel, atLeast(1)).chat(argThat((ChatRequest request) ->
                containsTool(request, "get_weather")
                        && !containsTool(request, "get_time")
        ));
    }

    private static boolean containsTool(ChatRequest chatRequest, String toolName) {
        return chatRequest.toolSpecifications().stream().anyMatch(t -> t.name().equals(toolName));
    }

    private Path toPath(String fileName) {
        try {
            return Paths.get(getClass().getClassLoader().getResource(fileName).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static Stream<String> getToolNames(List<ToolProvider> toolProviders) {
        return toolProviders.stream()
                .flatMap(tp -> tp.provideTools(null).tools().keySet().stream())
                .map(ToolSpecification::name);
    }
}
