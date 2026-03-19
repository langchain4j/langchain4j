package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.simple.SimpleToolSearchStrategy;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.service.AiServicesWithToolSearchToolIT.containsTool;
import static dev.langchain4j.service.AiServicesWithToolSearchToolIT.hasSearchableTools;
import static dev.langchain4j.service.StreamingAiServicesWithToolSearchToolIT.verifyNoMoreImportantInteractions;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SkillsWithToolSearchStreamingTest {

    interface Assistant {
        TokenStream chat(String userMessage);
    }

    @Test
    void activate_skill_should_be_visible_and_regular_tools_searchable() throws Exception {

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
        StreamingChatModelMock chatModel = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from("I don't need any skill."));
        StreamingChatModelMock spyChatModel = spy(chatModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .tools(Map.of(getWeather, (req, memoryId) -> "sunny"))
                .toolProvider(skills.toolProvider())
                .toolSearchStrategy(new SimpleToolSearchStrategy())
                .systemMessage("You have skills: " + skills.formatAvailableSkills())
                .build();

        // when
        chat(assistant, "Hello");

        // then - first (and only) LLM call has activate_skill + tool_search_tool, NOT getWeather
        verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "tool_search_tool")
                        && !containsTool(request, "getWeather")
        ), any());

        verifyNoMoreImportantInteractions(spyChatModel);
    }

    @Test
    void skill_scoped_tools_should_not_be_searchable_but_regular_tools_should() throws Exception {

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
        StreamingChatModelMock chatModel = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("tool_search_tool")
                        .arguments("{\"terms\": [\"inventory\"]}")
                        .build()),
                AiMessage.from("No inventory tool found via search.")
        );
        StreamingChatModelMock spyChatModel = spy(chatModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .tools(Map.of(getWeather, (req, memoryId) -> "sunny"))
                .toolProvider(skills.toolProvider())
                .toolSearchStrategy(spyStrategy)
                .systemMessage("You have skills: " + skills.formatAvailableSkills())
                .build();

        // when
        chat(assistant, "Search for inventory tools");

        // then - tool search should only have getWeather as searchable, NOT query_inventory
        var inOrder = inOrder(spyChatModel, spyStrategy);

        // LLM call 1: activate_skill + tool_search_tool visible
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "tool_search_tool")
                        && !containsTool(request, "query_inventory")
                        && !containsTool(request, "getWeather")
        ), any());

        // tool search is invoked — searchable pool has only getWeather, not query_inventory
        inOrder.verify(spyStrategy).search(argThat((ToolSearchRequest request) ->
                hasSearchableTools(request, "getWeather")
        ));

        // LLM call 2: search found nothing for "inventory" (only getWeather was searchable)
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "tool_search_tool")
                        && !containsTool(request, "query_inventory")
        ), any());

        verifyNoMoreImportantInteractions(spyChatModel);
    }

    @Test
    void skill_scoped_tools_should_appear_after_activation_with_tool_search() throws Exception {

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
        StreamingChatModelMock chatModel = StreamingChatModelMock.thatAlwaysStreams(
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
        StreamingChatModelMock spyChatModel = spy(chatModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .toolProvider(skills.toolProvider())
                .toolSearchStrategy(new SimpleToolSearchStrategy())
                .systemMessage("You have skills: " + skills.formatAvailableSkills())
                .build();

        // when
        ChatResponse response = chat(assistant, "Check inventory");

        // then
        assertThat(response.aiMessage().text()).contains("47");

        var inOrder = inOrder(spyChatModel);

        // LLM call 1: only activate_skill + tool_search_tool, no skill-scoped tools
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "tool_search_tool")
                        && !containsTool(request, "query_inventory")
        ), any());

        // LLM call 2: after activation, query_inventory now visible
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "query_inventory")
        ), any());

        // LLM call 3: query_inventory still visible
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "query_inventory")
        ), any());

        verifyNoMoreImportantInteractions(spyChatModel);
    }

    @Test
    void regular_tools_should_remain_searchable_after_skill_activation() throws Exception {

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
        StreamingChatModelMock chatModel = StreamingChatModelMock.thatAlwaysStreams(
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
        StreamingChatModelMock spyChatModel = spy(chatModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .tools(Map.of(getWeather, (req, memoryId) -> "sunny"))
                .toolProvider(skills.toolProvider())
                .toolSearchStrategy(spyStrategy)
                .systemMessage("You have skills: " + skills.formatAvailableSkills())
                .build();

        // when
        ChatResponse response = chat(assistant, "Activate inventory then check weather");

        // then
        assertThat(response.aiMessage().text()).contains("sunny");

        var inOrder = inOrder(spyChatModel, spyStrategy);

        // LLM call 1: activate_skill + tool_search_tool only
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "tool_search_tool")
                        && !containsTool(request, "query_inventory")
                        && !containsTool(request, "getWeather")
        ), any());

        // LLM call 2: skill activated, query_inventory now visible; calls tool_search_tool
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "query_inventory")
                        && !containsTool(request, "getWeather")
        ), any());

        // tool search: getWeather is searchable (regular tool), query_inventory is NOT (skill-scoped)
        inOrder.verify(spyStrategy).search(argThat((ToolSearchRequest request) ->
                hasSearchableTools(request, "getWeather")
        ));

        // LLM call 3: getWeather is now found and visible alongside query_inventory
        // LLM call 4: responds with text, same tools visible
        inOrder.verify(spyChatModel, times(2)).chat(argThat((ChatRequest request) ->
                containsTool(request, "query_inventory")
                        && containsTool(request, "getWeather")
        ), any());

        verifyNoMoreImportantInteractions(spyChatModel);
    }

    @Test
    void only_activated_skill_tools_should_appear_not_other_skills() throws Exception {

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
        StreamingChatModelMock chatModel = StreamingChatModelMock.thatAlwaysStreams(
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
        StreamingChatModelMock spyChatModel = spy(chatModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .toolProvider(skills.toolProvider())
                .toolSearchStrategy(new SimpleToolSearchStrategy())
                .systemMessage("You have skills: " + skills.formatAvailableSkills())
                .build();

        // when
        ChatResponse response = chat(assistant, "What's the weather?");

        // then
        assertThat(response.aiMessage().text()).contains("sunny");

        var inOrder = inOrder(spyChatModel);

        // LLM call 1: no skill-scoped tools yet
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                containsTool(request, "activate_skill")
                        && !containsTool(request, "get_weather")
                        && !containsTool(request, "get_time")
        ), any());

        // LLM call 2: only weather's get_weather appears, NOT time's get_time
        // LLM call 3: responds with text, same tools visible
        inOrder.verify(spyChatModel, times(2)).chat(argThat((ChatRequest request) ->
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
}
