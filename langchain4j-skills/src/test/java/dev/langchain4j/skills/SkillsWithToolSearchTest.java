package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.simple.SimpleToolSearchStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.langchain4j.agent.tool.SearchBehavior.ALWAYS_VISIBLE;
import static dev.langchain4j.agent.tool.ToolSpecification.METADATA_SEARCH_BEHAVIOR;
import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static dev.langchain4j.service.AiServicesWithToolSearchToolIT.containsTool;
import static dev.langchain4j.service.AiServicesWithToolSearchToolIT.hasSearchableTools;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SkillsWithToolSearchTest {

    interface Assistant {
        String chat(String userMessage);
    }

    @Test
    void skill_management_tools_should_have_always_visible_metadata() {

        // given
        Skills skills = Skills.from(
                Skill.builder()
                        .name("my-skill")
                        .description("A skill")
                        .content("instructions")
                        .resources(List.of(
                                SkillResource.builder()
                                        .relativePath("ref.md")
                                        .content("reference content")
                                        .build()))
                        .build()
        );

        // when
        ToolProviderResult result = skills.toolProvider().provideTools(dummyRequest());

        // then
        ToolSpecification activateSkill = result.toolSpecificationByName("activate_skill");
        assertThat(activateSkill).isNotNull();
        assertThat(activateSkill.metadata().get(METADATA_SEARCH_BEHAVIOR)).isEqualTo(ALWAYS_VISIBLE);

        ToolSpecification readResource = result.toolSpecificationByName("read_skill_resource");
        assertThat(readResource).isNotNull();
        assertThat(readResource.metadata().get(METADATA_SEARCH_BEHAVIOR)).isEqualTo(ALWAYS_VISIBLE);
    }

    @Test
    void activate_skill_should_be_visible_and_regular_tools_searchable() {

        // given
        ToolSpecification getWeather = ToolSpecification.builder()
                .name("getWeather").description("Gets weather").build();

        Skills skills = Skills.from(
                Skill.builder()
                        .name("my-skill")
                        .description("Describes how to use tools")
                        .content("instructions")
                        .build()
        );

        // LLM call 1: sees activate_skill + tool_search_tool, responds with text
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("I don't need any skill.");
        ChatModelMock spyChatModel = spy(chatModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .tools(Map.of(getWeather, (req, memoryId) -> "sunny"))
                .toolProvider(skills.toolProvider())
                .toolSearchStrategy(new SimpleToolSearchStrategy())
                .systemMessage("You have skills: " + skills.formatAvailableSkills())
                .build();

        // when
        assistant.chat("Hello");

        // then - first (and only) LLM call has activate_skill + tool_search_tool, NOT getWeather
        verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "tool_search_tool")
                        && !containsTool(request, "getWeather")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
    }

    @Test
    void skill_scoped_tools_should_not_be_searchable_but_regular_tools_should() {

        // given
        ToolSpecification getWeather = ToolSpecification.builder()
                .name("getWeather").description("Gets weather").build();

        Skills skills = Skills.from(
                Skill.builder()
                        .name("my-skill")
                        .description("A skill with tools")
                        .content("Use query_inventory tool")
                        .tools(Map.of(
                                ToolSpecification.builder().name("query_inventory")
                                        .description("Queries inventory").build(),
                                (req, memoryId) -> "47 units"
                        ))
                        .build()
        );

        SimpleToolSearchStrategy spyStrategy = spy(new SimpleToolSearchStrategy());

        // LLM call 1: calls tool_search_tool to search for "inventory"
        // LLM call 2: gets search result, responds with text
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("tool_search_tool")
                        .arguments("{\"terms\": [\"inventory\"]}")
                        .build()),
                AiMessage.from("No inventory tool found via search.")
        );
        ChatModelMock spyChatModel = spy(chatModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .tools(Map.of(getWeather, (req, memoryId) -> "sunny"))
                .toolProvider(skills.toolProvider())
                .toolSearchStrategy(spyStrategy)
                .systemMessage("You have skills: " + skills.formatAvailableSkills())
                .build();

        // when
        assistant.chat("Search for inventory tools");

        // then - tool search should only have getWeather as searchable, NOT query_inventory
        var inOrder = inOrder(spyChatModel, spyStrategy);

        // LLM call 1: activate_skill + tool_search_tool visible
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "tool_search_tool")
                        && !containsTool(request, "query_inventory")
                        && !containsTool(request, "getWeather")
        ));

        // tool search is invoked — searchable pool has only getWeather, not query_inventory
        inOrder.verify(spyStrategy).search(argThat((ToolSearchRequest request) ->
                hasSearchableTools(request, "getWeather")
        ));

        // LLM call 2: search found nothing for "inventory" (only getWeather was searchable)
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "tool_search_tool")
                        && !containsTool(request, "query_inventory")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
    }

    @Test
    void skill_scoped_tools_should_appear_after_activation_with_tool_search() {

        // given
        Skills skills = Skills.from(
                Skill.builder()
                        .name("inventory")
                        .description("Inventory management skill")
                        .content("Use query_inventory to check stock.")
                        .tools(Map.of(
                                ToolSpecification.builder().name("query_inventory")
                                        .description("Queries inventory").build(),
                                (req, memoryId) -> "47 units"
                        ))
                        .build()
        );

        // LLM call 1: calls activate_skill
        // LLM call 2: calls query_inventory (now visible after activation)
        // LLM call 3: responds with text
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("activate_skill")
                        .arguments("{\"skill_name\": \"inventory\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("query_inventory")
                        .arguments("{}")
                        .build()),
                AiMessage.from("There are 47 units in stock.")
        );
        ChatModelMock spyChatModel = spy(chatModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .toolProvider(skills.toolProvider())
                .toolSearchStrategy(new SimpleToolSearchStrategy())
                .systemMessage("You have skills: " + skills.formatAvailableSkills())
                .build();

        // when
        String answer = assistant.chat("Check inventory");

        // then
        assertThat(answer).contains("47");

        var inOrder = inOrder(spyChatModel);

        // LLM call 1: only activate_skill + tool_search_tool, no skill-scoped tools
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "tool_search_tool")
                        && !containsTool(request, "query_inventory")
        ));

        // LLM call 2: after activation, query_inventory now visible
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "query_inventory")
        ));

        // LLM call 3: query_inventory still visible
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "query_inventory")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
    }

    @Test
    void regular_tools_should_remain_searchable_after_skill_activation() {

        // given
        ToolSpecification getWeather = ToolSpecification.builder()
                .name("getWeather").description("Gets weather forecast").build();

        Skills skills = Skills.from(
                Skill.builder()
                        .name("inventory")
                        .description("Inventory management skill")
                        .content("Use query_inventory to check stock.")
                        .tools(Map.of(
                                ToolSpecification.builder().name("query_inventory")
                                        .description("Queries inventory").build(),
                                (req, memoryId) -> "47 units"
                        ))
                        .build()
        );

        SimpleToolSearchStrategy spyStrategy = spy(new SimpleToolSearchStrategy());

        // LLM call 1: calls activate_skill
        // LLM call 2: calls tool_search_tool to find weather tool
        // LLM call 3: calls getWeather (now found via search)
        // LLM call 4: responds with text
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("activate_skill")
                        .arguments("{\"skill_name\": \"inventory\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("tool_search_tool")
                        .arguments("{\"terms\": [\"weather\"]}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("getWeather")
                        .arguments("{}")
                        .build()),
                AiMessage.from("The weather is sunny and we have 47 units.")
        );
        ChatModelMock spyChatModel = spy(chatModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .tools(Map.of(getWeather, (req, memoryId) -> "sunny"))
                .toolProvider(skills.toolProvider())
                .toolSearchStrategy(spyStrategy)
                .systemMessage("You have skills: " + skills.formatAvailableSkills())
                .build();

        // when
        String answer = assistant.chat("Activate inventory then check weather");

        // then
        assertThat(answer).contains("sunny");

        var inOrder = inOrder(spyChatModel, spyStrategy);

        // LLM call 1: activate_skill + tool_search_tool only
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "tool_search_tool")
                        && !containsTool(request, "query_inventory")
                        && !containsTool(request, "getWeather")
        ));

        // LLM call 2: skill activated, query_inventory now visible; calls tool_search_tool
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "query_inventory")
                        && !containsTool(request, "getWeather")
        ));

        // tool search: getWeather is searchable (regular tool), query_inventory is NOT (skill-scoped)
        inOrder.verify(spyStrategy).search(argThat((ToolSearchRequest request) ->
                hasSearchableTools(request, "getWeather")
        ));

        // LLM call 3: getWeather is now found and visible alongside query_inventory
        // LLM call 4: responds with text, same tools visible
        inOrder.verify(spyChatModel, times(2)).chat(argThat((ChatRequest request) ->
                containsTool(request, "query_inventory")
                        && containsTool(request, "getWeather")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
    }

    @Test
    void only_activated_skill_tools_should_appear_not_other_skills() {

        // given
        Skills skills = Skills.from(
                Skill.builder()
                        .name("weather")
                        .description("Weather skill")
                        .content("Use get_weather tool.")
                        .tools(Map.of(
                                ToolSpecification.builder().name("get_weather")
                                        .description("Gets weather").build(),
                                (req, memoryId) -> "sunny"
                        ))
                        .build(),
                Skill.builder()
                        .name("time")
                        .description("Time skill")
                        .content("Use get_time tool.")
                        .tools(Map.of(
                                ToolSpecification.builder().name("get_time")
                                        .description("Gets time").build(),
                                (req, memoryId) -> "12:00"
                        ))
                        .build()
        );

        // LLM call 1: activates weather skill only
        // LLM call 2: calls get_weather
        // LLM call 3: responds with text
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("activate_skill")
                        .arguments("{\"skill_name\": \"weather\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("get_weather")
                        .arguments("{}")
                        .build()),
                AiMessage.from("It is sunny.")
        );
        ChatModelMock spyChatModel = spy(chatModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .toolProvider(skills.toolProvider())
                .toolSearchStrategy(new SimpleToolSearchStrategy())
                .systemMessage("You have skills: " + skills.formatAvailableSkills())
                .build();

        // when
        String answer = assistant.chat("What's the weather?");

        // then
        assertThat(answer).contains("sunny");

        var inOrder = inOrder(spyChatModel);

        // LLM call 1: no skill-scoped tools yet
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && !containsTool(request, "get_weather")
                        && !containsTool(request, "get_time")
        ));

        // LLM call 2: only weather's get_weather appears, NOT time's get_time
        // LLM call 3: responds with text, same tools visible
        inOrder.verify(spyChatModel, times(2)).chat(argThat((ChatRequest request) ->
                containsTool(request, "get_weather")
                        && !containsTool(request, "get_time")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
    }

    private static ToolProviderRequest dummyRequest() {
        return ToolProviderRequest.builder()
                .invocationContext(InvocationContext.builder().build())
                .userMessage(UserMessage.from("test"))
                .build();
    }
}
