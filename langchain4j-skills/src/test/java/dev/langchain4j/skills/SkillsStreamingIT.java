package dev.langchain4j.skills;

import dev.langchain4j.LoggingChatModelListener;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_SONNET_4_6;
import static dev.langchain4j.service.StreamingAiServicesWithToolSearchToolIT.verifyNoMoreImportantInteractions;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class SkillsStreamingIT {

    StreamingChatModel model = AnthropicStreamingChatModel.builder()
            .apiKey(System.getenv("ANTHROPIC_API_KEY"))
            .modelName(CLAUDE_SONNET_4_6)
            .listeners(new LoggingChatModelListener())
            .build();

    interface Assistant {

        TokenStream chat(String userMessage);
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
    void should_activate_skill_and_load_resource() throws Exception {

        // given
        Skill skill = FileSystemSkillLoader.loadSkill(toPath("skills/using-process-tool"));

        // when
        Skills skills = Skills.from(skill);

        // then
        assertThat(skills.formatAvailableSkills()).contains("using-process-tool");
        assertThat(getToolNames(skills.toolProvider()))
                .containsExactlyInAnyOrder("activate_skill", "read_skill_resource");

        // given
        Tools spyTools = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(model)
                .systemMessage("You have access to the following skills: " + skills.formatAvailableSkills())
                .tools(spyTools)
                .toolProvider(skills.toolProvider())
                .build();

        // when
        chat(assistant, "Use 'process' tool for Klaus Heisler");

        // then
        verify(spyTools).generate("Heisler", "Klaus");
        verify(spyTools).process("Klaus", 177, "Heisler");
        verify(spyTools).reset();
        verifyNoMoreInteractions(spyTools);
    }

    @Test
    void should_activate_skill_and_load_resource__programmatic() throws Exception {

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
                .streamingChatModel(model)
                .systemMessage("You have access to the following skills: " + skills.formatAvailableSkills())
                .tools(spyTools)
                .toolProvider(skills.toolProvider())
                .build();

        // when
        chat(assistant, "Use 'process' tool for Klaus Heisler");

        // then
        verify(spyTools).generate("Heisler", "Klaus");
        verify(spyTools).process("Klaus", 177, "Heisler");
        verify(spyTools).reset();
        verifyNoMoreInteractions(spyTools);
    }

    @Test
    void should_activate_multiple_skills() throws Exception {

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
                .streamingChatModel(model)
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
        chat(assistant, "Use 'poll' tool");

        // then
        verify(spyTools).poll();
        verify(spyTools).generate("Heisler", "Klaus");
        verify(spyTools).process("Klaus", 177, "Heisler");
        verify(spyTools).reset();
        verifyNoMoreInteractions(spyTools);
    }

    @Test
    void should_not_include_skill_scoped_tools_before_activation_and_include_after() throws Exception {

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

        StreamingChatModel spyChatModel = spy(model);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .systemMessage("""
                        You have access to the following skills:
                        %s
                        When the user's request relates to one of these skills,
                        activate it first using the 'activate_skill' tool before proceeding.
                        """.formatted(skills.formatAvailableSkills()))
                .toolProvider(skills.toolProvider())
                .build();

        // when
        chat(assistant, "Check the inventory for widgets");

        // then
        InOrder inOrder = inOrder(spyChatModel);

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && !containsTool(request, "query_inventory")
        ), any());

        inOrder.verify(spyChatModel, times(2)).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "query_inventory")
        ), any());

        verifyNoMoreImportantInteractions(spyChatModel);

        // when
        chat(assistant, "Now check the inventory for gadgets");

        // then
        inOrder.verify(spyChatModel, times(2)).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "query_inventory")
        ), any());

        verifyNoMoreImportantInteractions(spyChatModel);
    }

    @Test
    void should_not_include_skill_tools_for_unrelated_skill() throws Exception {

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

        StreamingChatModel spyChatModel = spy(model);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(spyChatModel)
                .systemMessage("""
                        You have access to the following skills:
                        %s
                        When the user's request relates to one of these skills,
                        activate it first using the 'activate_skill' tool before proceeding.
                        Activate only the relevant skill. Do NOT activate unrelated skills.
                        """.formatted(skills.formatAvailableSkills()))
                .toolProvider(skills.toolProvider())
                .build();

        // when
        ChatResponse response = chat(assistant, "What is the weather?");

        // then
        assertThat(response.aiMessage().text().toLowerCase()).contains("sunny");

        InOrder inOrder = inOrder(spyChatModel);

        // First call: only activate_skill, no skill-specific tools
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && !containsTool(request, "get_weather")
                        && !containsTool(request, "get_time")
        ), any());

        // After activating weather skill: get_weather should appear, get_time should NOT
        inOrder.verify(spyChatModel, atLeast(1)).chat(argThat((ChatRequest request) ->
                containsTool(request, "get_weather")
                        && !containsTool(request, "get_time")
        ), any());

        verifyNoMoreImportantInteractions(spyChatModel);
    }

    private static ChatResponse chat(Assistant assistant, String userMessage) throws Exception {
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
        assistant
                .chat(userMessage)
                .onCompleteResponse(futureResponse::complete)
                .onError(futureResponse::completeExceptionally)
                .start();
        return futureResponse.get(60, SECONDS);
    }

    @Test
    void skills_with_overlapping_tools_should_not_produce_duplicates() throws Exception {

        // given - two skills share 'query_inventory' tool
        Skill inventorySkill = Skill.builder()
                .name("inventory")
                .description("Inventory management")
                .content("Use query_inventory to check stock and reorder_item to reorder.")
                .toolProviders(request -> ToolProviderResult.builder()
                        .add(ToolSpecification.builder()
                                        .name("query_inventory")
                                        .description("Queries inventory levels")
                                        .build(),
                                (req, memoryId) -> "47 units")
                        .add(ToolSpecification.builder()
                                        .name("reorder_item")
                                        .description("Reorders an item")
                                        .build(),
                                (req, memoryId) -> "reordered")
                        .build())
                .build();

        Skill reportingSkill = Skill.builder()
                .name("reporting")
                .description("Reporting")
                .content("Use query_inventory to get data and generate_report to produce a report.")
                .toolProviders(request -> ToolProviderResult.builder()
                        .add(ToolSpecification.builder()
                                        .name("query_inventory")
                                        .description("Queries inventory levels")
                                        .build(),
                                (req, memoryId) -> "47 units")
                        .add(ToolSpecification.builder()
                                        .name("generate_report")
                                        .description("Generates a report")
                                        .build(),
                                (req, memoryId) -> "report generated")
                        .build())
                .build();

        Skills skills = Skills.from(inventorySkill, reportingSkill);

        StreamingChatModel spyChatModel = spy(model);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .systemMessage("""
                        You have access to the following skills:
                        %s
                        When the user's request relates to one of these skills,
                        activate it first using the 'activate_skill' tool before proceeding.
                        You can activate multiple skills if needed.
                        """.formatted(skills.formatAvailableSkills()))
                .toolProvider(skills.toolProvider())
                .build();

        // when - activate both skills
        chat(assistant, "Activate both inventory and reporting skills, then query the inventory");

        // then
        InOrder inOrder = inOrder(spyChatModel);

        // first call: only activate_skill, no skill-scoped tools
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && !containsTool(request, "query_inventory")
                        && !containsTool(request, "reorder_item")
                        && !containsTool(request, "generate_report")
        ), any());

        // after activation(s): all unique tools present, no duplicates
        inOrder.verify(spyChatModel, atLeast(1)).chat(argThat((ChatRequest request) -> {
            List<String> toolNames = request.toolSpecifications().stream()
                    .map(ToolSpecification::name)
                    .toList();
            return toolNames.contains("query_inventory")
                    && toolNames.contains("reorder_item")
                    && toolNames.contains("generate_report")
                    && toolNames.stream().filter("query_inventory"::equals).count() == 1;
        }), any());

        verifyNoMoreImportantInteractions(spyChatModel);

        // when - second AI Service invocation
        chat(assistant, "Now generate a report");

        // then - all tools still active, no duplicates
        inOrder.verify(spyChatModel, atLeast(1)).chat(argThat((ChatRequest request) -> {
            List<String> toolNames = request.toolSpecifications().stream()
                    .map(ToolSpecification::name)
                    .toList();
            return toolNames.contains("query_inventory")
                    && toolNames.contains("reorder_item")
                    && toolNames.contains("generate_report")
                    && toolNames.stream().filter("query_inventory"::equals).count() == 1;
        }), any());

        verifyNoMoreImportantInteractions(spyChatModel);
    }

    @Test
    void activating_same_skill_twice_should_not_produce_duplicate_tools() throws Exception {

        // given
        Skill skill = Skill.builder()
                .name("inventory")
                .description("Inventory management skill")
                .content("Use query_inventory to check stock.")
                .tools(Map.of(
                        ToolSpecification.builder().name("query_inventory")
                                .description("Queries inventory").build(),
                        (req, memoryId) -> "47 units"
                ))
                .build();

        Skills skills = Skills.from(skill);

        StreamingChatModelMock chatModel = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("activate_skill")
                        .arguments("{\"skill_name\": \"inventory\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("activate_skill")
                        .arguments("{\"skill_name\": \"inventory\"}")
                        .build()),
                AiMessage.from("Done.")
        );
        StreamingChatModelMock spyChatModel = spy(chatModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .systemMessage("""
                        You have access to the following skills:
                        %s
                        When the user's request relates to one of these skills,
                        activate it first using the 'activate_skill' tool before proceeding.
                        """.formatted(skills.formatAvailableSkills()))
                .toolProvider(skills.toolProvider())
                .build();

        // when
        chat(assistant, "Activate inventory twice");

        // then
        InOrder inOrder = inOrder(spyChatModel);

        // first call: no skill tools yet
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && !containsTool(request, "query_inventory")
        ), any());

        // subsequent calls: query_inventory visible, no duplicates
        inOrder.verify(spyChatModel, times(2)).chat(argThat((ChatRequest request) -> {
            long count = request.toolSpecifications().stream()
                    .filter(t -> t.name().equals("query_inventory"))
                    .count();
            return containsTool(request, "query_inventory") && count == 1;
        }), any());

        verifyNoMoreImportantInteractions(spyChatModel);
    }

    @Test
    void activating_invalid_skill_should_return_error_to_llm() throws Exception {

        // given
        Skills skills = Skills.from(
                Skill.builder()
                        .name("inventory")
                        .description("Inventory management skill")
                        .content("Use query_inventory to check stock.")
                        .build()
        );

        StreamingChatModelMock chatModel = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("activate_skill")
                        .arguments("{\"skill_name\": \"non-existent\"}")
                        .build()),
                AiMessage.from("Sorry, that skill doesn't exist.")
        );
        StreamingChatModelMock spyChatModel = spy(chatModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .systemMessage("You have skills: " + skills.formatAvailableSkills())
                .toolProvider(skills.toolProvider())
                .build();

        // when
        ChatResponse response = chat(assistant, "Activate the foo skill");

        // then
        assertThat(response.aiMessage().text()).contains("doesn't exist");

        var inOrder = inOrder(spyChatModel);

        // LLM call 1: activate_skill with invalid name
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
        ), any());

        // LLM call 2: error message sent back containing available skill names
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.messages().stream()
                        .filter(msg -> msg instanceof ToolExecutionResultMessage)
                        .map(msg -> (ToolExecutionResultMessage) msg)
                        .anyMatch(msg -> msg.text().contains("'inventory'")
                                && msg.text().contains("non-existent"))
        ), any());

        verifyNoMoreImportantInteractions(spyChatModel);
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

    private static Stream<String> getToolNames(ToolProvider toolProvider) {
        return toolProvider.provideTools(null).tools().keySet().stream().map(ToolSpecification::name);
    }
}
