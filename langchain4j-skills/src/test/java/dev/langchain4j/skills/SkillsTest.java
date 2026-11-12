package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class SkillsTest {

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

        ChatModelMock chatModelMock = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("activate_skill")
                        .arguments("{\"skill_name\": \"using-process-tool\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("generate")
                        .arguments("{\"arg0\": \"Heisler\", \"arg1\": \"Klaus\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("process")
                        .arguments("{\"arg0\": \"Klaus\", \"arg1\": 177, \"arg2\": \"Heisler\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("read_skill_resource")
                        .arguments("{\"skill_name\": \"using-process-tool\", \"relative_path\": \"references/25.md\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("reset")
                        .arguments("{}")
                        .build()),
                AiMessage.from("Done.")
        );

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModelMock)
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

        ChatModelMock chatModelMock = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("activate_skill")
                        .arguments("{\"skill_name\": \"using-process-tool\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("generate")
                        .arguments("{\"arg0\": \"Heisler\", \"arg1\": \"Klaus\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("process")
                        .arguments("{\"arg0\": \"Klaus\", \"arg1\": 177, \"arg2\": \"Heisler\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("read_skill_resource")
                        .arguments("{\"skill_name\": \"using-process-tool\", \"relative_path\": \"references/25.md\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("reset")
                        .arguments("{}")
                        .build()),
                AiMessage.from("Done.")
        );

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModelMock)
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

        ChatModelMock chatModelMock = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("activate_skill")
                        .arguments("{\"skill_name\": \"using-poll-tool\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("poll")
                        .arguments("{}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("activate_skill")
                        .arguments("{\"skill_name\": \"using-process-tool\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("generate")
                        .arguments("{\"arg0\": \"Heisler\", \"arg1\": \"Klaus\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("process")
                        .arguments("{\"arg0\": \"Klaus\", \"arg1\": 177, \"arg2\": \"Heisler\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("read_skill_resource")
                        .arguments("{\"skill_name\": \"using-process-tool\", \"relative_path\": \"references/25.md\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("reset")
                        .arguments("{}")
                        .build()),
                AiMessage.from("Done.")
        );

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModelMock)
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

    @Test
    void should_not_include_skill_scoped_tools_before_activation_and_include_after__tool_provider() {

        // given
        Skill skill = Skill.builder()
                .name("inventory-management")
                .description("Describes how to query and manage the internal inventory system")
                .content("When asked about inventory or stock levels, use the 'query_inventory' tool.")
                .toolProviders(request -> ToolProviderResult.builder()
                        .add(ToolSpecification.builder()
                                        .name("query_inventory")
                                        .description("Queries the internal inventory system for stock levels")
                                        .build(),
                                (req, memoryId) -> "47 units in stock")
                        .build())
                .build();

        verifyToolsHiddenBeforeActivationAndVisibleAfter(skill);
    }

    @Test
    void should_not_include_skill_scoped_tools_before_activation_and_include_after__tools_map() {

        // given
        Skill skill = Skill.builder()
                .name("inventory-management")
                .description("Describes how to query and manage the internal inventory system")
                .content("When asked about inventory or stock levels, use the 'query_inventory' tool.")
                .tools(java.util.Map.of(
                        ToolSpecification.builder()
                                .name("query_inventory")
                                .description("Queries the internal inventory system for stock levels")
                                .build(),
                        (req, memoryId) -> "47 units in stock"
                ))
                .build();

        verifyToolsHiddenBeforeActivationAndVisibleAfter(skill);
    }

    @Test
    void should_not_include_skill_scoped_tools_before_activation_and_include_after__tool_annotated_object() {

        // given
        Skill skill = Skill.builder()
                .name("inventory-management")
                .description("Describes how to query and manage the internal inventory system")
                .content("When asked about inventory or stock levels, use the 'query_inventory' tool.")
                .tools(new InventoryTools())
                .build();

        verifyToolsHiddenBeforeActivationAndVisibleAfter(skill);
    }

    @Test
    void should_not_include_skill_scoped_tools_before_activation_and_include_after__combined() {

        // given
        Skill skill = Skill.builder()
                .name("inventory-management")
                .description("Describes how to query and manage the internal inventory system")
                .content("When asked about inventory or stock levels, use the 'query_inventory' tool.")
                .tools(new InventoryTools())
                .toolProviders(request -> ToolProviderResult.builder()
                        .add(ToolSpecification.builder()
                                        .name("update_inventory")
                                        .description("Updates inventory stock level")
                                        .build(),
                                (req, memoryId) -> "updated")
                        .build())
                .build();

        Skills skills = Skills.from(skill);

        ChatModelMock chatModelMock = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("activate_skill")
                        .arguments("{\"skill_name\": \"inventory-management\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("query_inventory")
                        .arguments("{}")
                        .build()),
                AiMessage.from("There are 47 units in stock for widgets.")
        );
        ChatModel spyChatModel = spy(chatModelMock);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
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
        assistant.chat("Check the inventory for widgets");

        // then
        InOrder inOrder = inOrder(spyChatModel);

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && !containsTool(request, "query_inventory")
                        && !containsTool(request, "update_inventory")
        ));

        inOrder.verify(spyChatModel, atLeast(1)).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "query_inventory")
                        && containsTool(request, "update_inventory")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
    }

    static class InventoryTools {

        @Tool("Queries the internal inventory system for stock levels")
        String query_inventory() {
            return "47 units in stock";
        }
    }

    private void verifyToolsHiddenBeforeActivationAndVisibleAfter(Skill skill) {
        Skills skills = Skills.from(skill);

        ChatModelMock chatModelMock = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("activate_skill")
                        .arguments("{\"skill_name\": \"inventory-management\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("query_inventory")
                        .arguments("{}")
                        .build()),
                AiMessage.from("There are 47 units in stock for widgets."),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("query_inventory")
                        .arguments("{}")
                        .build()),
                AiMessage.from("There are 47 units in stock for gadgets.")
        );
        ChatModel spyChatModel = spy(chatModelMock);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
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
        assistant.chat("Check the inventory for widgets");

        // then
        InOrder inOrder = inOrder(spyChatModel);

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && !containsTool(request, "query_inventory")
        ));

        inOrder.verify(spyChatModel, times(2)).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "query_inventory")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);

        // when
        assistant.chat("Now check the inventory for gadgets");

        // then
        inOrder.verify(spyChatModel, times(2)).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "query_inventory")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
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

        ChatModelMock chatModelMock = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("activate_skill")
                        .arguments("{\"skill_name\": \"weather\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("get_weather")
                        .arguments("{}")
                        .build()),
                AiMessage.from("The weather is sunny.")
        );
        ChatModel spyChatModel = spy(chatModelMock);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
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

        verifyNoMoreInteractionsFor(spyChatModel);
    }

    @Test
    void normal_tools_should_always_be_present_regardless_of_skill_activation() {

        // given
        ToolSpecification skillTool = ToolSpecification.builder()
                .name("query_inventory")
                .description("Queries the internal inventory system for stock levels")
                .build();

        Skill skill = Skill.builder()
                .name("inventory-management")
                .description("Describes how to query and manage the internal inventory system")
                .content("When asked about inventory or stock levels, use the 'query_inventory' tool.")
                .toolProviders(request -> ToolProviderResult.builder()
                        .add(skillTool, (req, memoryId) -> "47 units in stock")
                        .build())
                .build();

        Skills skills = Skills.from(skill);

        Tools spyTools = spy(new Tools());

        ChatModelMock chatModelMock = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("activate_skill")
                        .arguments("{\"skill_name\": \"inventory-management\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("query_inventory")
                        .arguments("{}")
                        .build()),
                AiMessage.from("There are 47 units in stock.")
        );
        ChatModel spyChatModel = spy(chatModelMock);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
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
        assistant.chat("Check the inventory for widgets");

        // then - normal tools (process, generate, finish, reset, poll) should be present in every LLM call
        InOrder inOrder = inOrder(spyChatModel);

        // first call: normal tools + activate_skill, but NOT query_inventory (skill not yet activated)
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "process")
                        && containsTool(request, "generate")
                        && containsTool(request, "finish")
                        && containsTool(request, "reset")
                        && containsTool(request, "poll")
                        && !containsTool(request, "query_inventory")
        ));

        // after activation: normal tools still present alongside skill-scoped tool
        inOrder.verify(spyChatModel, atLeast(1)).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "process")
                        && containsTool(request, "generate")
                        && containsTool(request, "query_inventory")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
    }

    @Test
    void skills_with_overlapping_tools_should_not_produce_duplicates() {

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

        ChatModelMock chatModelMock = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(
                        ToolExecutionRequest.builder()
                                .name("activate_skill")
                                .arguments("{\"skill_name\": \"inventory\"}")
                                .build(),
                        ToolExecutionRequest.builder()
                                .name("activate_skill")
                                .arguments("{\"skill_name\": \"reporting\"}")
                                .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("query_inventory")
                        .arguments("{}")
                        .build()),
                AiMessage.from("Inventory has 47 units."),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("generate_report")
                        .arguments("{}")
                        .build()),
                AiMessage.from("Report generated.")
        );
        ChatModel spyChatModel = spy(chatModelMock);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
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
        assistant.chat("Activate both inventory and reporting skills, then query the inventory");

        // then
        InOrder inOrder = inOrder(spyChatModel);

        // first call: only activate_skill, no skill-scoped tools
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && !containsTool(request, "query_inventory")
                        && !containsTool(request, "reorder_item")
                        && !containsTool(request, "generate_report")
        ));

        // after activation(s): all unique tools present, no duplicates
        inOrder.verify(spyChatModel, atLeast(1)).chat(argThat((ChatRequest request) -> {
            List<String> toolNames = request.toolSpecifications().stream()
                    .map(ToolSpecification::name)
                    .toList();
            return toolNames.contains("query_inventory")
                    && toolNames.contains("reorder_item")
                    && toolNames.contains("generate_report")
                    && toolNames.stream().filter("query_inventory"::equals).count() == 1;
        }));

        verifyNoMoreInteractionsFor(spyChatModel);

        // when - second AI Service invocation
        assistant.chat("Now generate a report");

        // then - all tools still active, no duplicates
        inOrder.verify(spyChatModel, atLeast(1)).chat(argThat((ChatRequest request) -> {
            List<String> toolNames = request.toolSpecifications().stream()
                    .map(ToolSpecification::name)
                    .toList();
            return toolNames.contains("query_inventory")
                    && toolNames.contains("reorder_item")
                    && toolNames.contains("generate_report")
                    && toolNames.stream().filter("query_inventory"::equals).count() == 1;
        }));

        verifyNoMoreInteractionsFor(spyChatModel);
    }

    @Test
    void activating_same_skill_twice_should_not_produce_duplicate_tools() {

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

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
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
        ChatModelMock spyChatModel = spy(chatModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
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
        assistant.chat("Activate inventory twice");

        // then
        InOrder inOrder = inOrder(spyChatModel);

        // first call: no skill tools yet
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && !containsTool(request, "query_inventory")
        ));

        // subsequent calls: query_inventory visible, no duplicates
        inOrder.verify(spyChatModel, times(2)).chat(argThat((ChatRequest request) -> {
            long count = request.toolSpecifications().stream()
                    .filter(t -> t.name().equals("query_inventory"))
                    .count();
            return containsTool(request, "query_inventory") && count == 1;
        }));

        verifyNoMoreInteractionsFor(spyChatModel);
    }

    @Test
    void activating_invalid_skill_should_return_error_to_llm() {

        // given
        Skills skills = Skills.from(
                Skill.builder()
                        .name("inventory")
                        .description("Inventory management skill")
                        .content("Use query_inventory to check stock.")
                        .build()
        );

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("activate_skill")
                        .arguments("{\"skill_name\": \"non-existent\"}")
                        .build()),
                AiMessage.from("Sorry, that skill doesn't exist.")
        );
        ChatModelMock spyChatModel = spy(chatModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .systemMessage("You have skills: " + skills.formatAvailableSkills())
                .toolProvider(skills.toolProvider())
                .build();

        // when
        Result<String> result = assistant.chat("Activate the foo skill");

        // then
        assertThat(result.content()).contains("doesn't exist");

        var inOrder = inOrder(spyChatModel);

        // LLM call 1: activate_skill with invalid name
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
        ));

        // LLM call 2: error message sent back containing available skill names
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.messages().stream()
                        .filter(msg -> msg instanceof ToolExecutionResultMessage)
                        .map(msg -> (ToolExecutionResultMessage) msg)
                        .anyMatch(msg -> msg.text().contains("'inventory'")
                                && msg.text().contains("non-existent"))
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
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
