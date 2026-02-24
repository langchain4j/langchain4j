package dev.langchain4j.service;

import dev.langchain4j.LoggingChatModelListener;
import dev.langchain4j.agent.tool.SearchBehavior;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.ToolSearchResult;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import dev.langchain4j.service.tool.search.simple.SimpleToolSearchStrategy;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static dev.langchain4j.MockitoUtils.ignoreInteractions;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.service.AiServicesWithToolSearchToolIT.containsTool;
import static dev.langchain4j.service.AiServicesWithToolSearchToolIT.hasSearchableTools;
import static dev.langchain4j.service.AiServicesWithToolSearchToolIT.hasToolSearch;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class StreamingAiServicesWithToolSearchToolIT {

    static List<StreamingChatModel> models() {
        return List.of(
                OpenAiStreamingChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .modelName(GPT_4_O_MINI)
                        .temperature(0.0)
                        .listeners(new LoggingChatModelListener())
                        .build(),
                OpenAiStreamingChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .modelName(GPT_4_O_MINI)
                        .strictTools(true)
                        .temperature(0.0)
                        .listeners(new LoggingChatModelListener())
                        .build()
        );
    }

    interface AssistantWithToolSearch {

        @SystemMessage("Use 'tool_search_tool' tool if you need to discover other available tools. {{instructions}}")
        TokenStream chat(@UserMessage String userMessage, @V("instructions") String instructions);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__keep_previously_found_tools_in_chat_memory(StreamingChatModel model) throws Exception {

        // given
        StreamingChatModel spyModel = spy(model);
        AiServicesWithToolSearchToolIT.SearchableTools spyTools = spy(new AiServicesWithToolSearchToolIT.SearchableTools());
        ToolSearchStrategy spyToolSearchStrategy = spy(new SimpleToolSearchStrategy());
        Consumer<BeforeToolExecution> spyBeforeToolExecution = spy(new AiServicesWithToolSearchToolIT.BeforeToolExecutionCallback());
        Consumer<ToolExecution> spyAfterToolExecution = spy(new AiServicesWithToolSearchToolIT.AfterToolExecutionCallback());

        AssistantWithToolSearch assistant = AiServices.builder(AssistantWithToolSearch.class)
                .streamingChatModel(spyModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .tools(spyTools)
                .toolSearchStrategy(spyToolSearchStrategy)
                .beforeToolExecution(spyBeforeToolExecution)
                .afterToolExecution(spyAfterToolExecution)
                .build();

        TestTokenStreamHandler handler = spy(TestTokenStreamHandler.class);

        // when
        ChatResponse chatResponse = chat(assistant, "What is the weather in London?", handler);

        // then
        assertThat(chatResponse.aiMessage().text().toLowerCase()).contains("sun");

        InOrder inOrder = inOrder(spyModel, spyToolSearchStrategy, spyTools, spyBeforeToolExecution, spyAfterToolExecution, handler);

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, "tool_search_tool")
        ), any());

        inOrder.verify(spyBeforeToolExecution).accept(argThat(bte ->
                bte.request().name().equals("tool_search_tool")
                        && bte.request().arguments().contains("weather")
        ));
        inOrder.verify(handler).beforeToolExecution(argThat(bte ->
                bte.request().name().equals("tool_search_tool")
                        && bte.request().arguments().contains("weather")
        ));

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "weather")
                        && hasSearchableTools(request, "getWeather", "getTime")
        ));

        inOrder.verify(spyAfterToolExecution).accept(argThat(te ->
                te.request().name().equals("tool_search_tool")
                        && te.result().equals("Tools found: getWeather")
        ));
        inOrder.verify(handler).onToolExecuted(argThat(te ->
                te.request().name().equals("tool_search_tool")
                        && te.result().equals("Tools found: getWeather")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")

                        && request.messages().size() == 4
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals("tool_search_tool")
                        && toolResultMessage.text().equals("Tools found: getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getWeather"))
        ), any());

        inOrder.verify(spyTools).getWeather("London");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        verifyNoMoreInteractions(spyTools);

        CompletableFuture<ChatResponse> futureResponse2 = new CompletableFuture<>();

        // when
        ChatResponse chatResponse2 = chat(assistant, "What is the time in London?", handler);

        // then
        assertThat(chatResponse2.aiMessage().text()).contains("12", "34");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ), any());

        inOrder.verify(spyBeforeToolExecution).accept(argThat(bte ->
                bte.request().name().equals("tool_search_tool")
                        && bte.request().arguments().contains("time")
        ));
        inOrder.verify(handler).beforeToolExecution(argThat(bte ->
                bte.request().name().equals("tool_search_tool")
                        && bte.request().arguments().contains("time")
        ));

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "time")
                        && hasSearchableTools(request, "getTime") // no getWeather tool, as it was found already
        ));

        inOrder.verify(spyAfterToolExecution).accept(argThat(te ->
                te.request().name().equals("tool_search_tool")
                        && te.result().equals("Tools found: getTime")
        ));
        inOrder.verify(handler).onToolExecuted(argThat(te ->
                te.request().name().equals("tool_search_tool")
                        && te.result().equals("Tools found: getTime")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        inOrder.verify(spyTools).getTime("London");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        verifyNoMoreInteractions(spyTools);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__keep_previously_found_tools_in_chat_memory__tool_provider(StreamingChatModel model) throws Exception {

        // given
        ToolProvider toolProvider = new AiServicesWithToolSearchToolIT.SearchableTools.SearchableToolsProvider();
        StreamingChatModel spyModel = spy(model);
        ToolSearchStrategy spyToolSearchStrategy = spy(new SimpleToolSearchStrategy());

        AssistantWithToolSearch assistant = AiServices.builder(AssistantWithToolSearch.class)
                .streamingChatModel(spyModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .toolProvider(toolProvider)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        // when
        ChatResponse chatResponse = chat(assistant, "What is the weather in London?");

        // then
        assertThat(chatResponse.aiMessage().text().toLowerCase()).contains("sun");

        InOrder inOrder = inOrder(spyModel, spyToolSearchStrategy);

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, "tool_search_tool")
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "weather")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")

                        && request.messages().size() == 4
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals("tool_search_tool")
                        && toolResultMessage.text().equals("Tools found: getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getWeather"))
        ), any());

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);

        // when
        ChatResponse chatResponse2 = chat(assistant, "What is the time in London?");

        // then
        assertThat(chatResponse2.aiMessage().text()).contains("12", "34");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "time")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__multiple_simultaneous_tool_searches(StreamingChatModel model) throws Exception {

        // given
        StreamingChatModel spyModel = spy(model);
        AiServicesWithToolSearchToolIT.SearchableTools spyTools = spy(new AiServicesWithToolSearchToolIT.SearchableTools());
        ToolSearchStrategy spyToolSearchStrategy = spy(new SimpleToolSearchStrategy());

        String instructions = """
                Use separate tool calls for separate search terms.
                For example, when asked "What is the weather and time in London?",
                call 'tool_search_tool' twice (simultaneously, in parallel),
                once with ["London", "weather"] arguments, once with ["London", "time"] arguments.
                """;

        AssistantWithToolSearch assistant = AiServices.builder(AssistantWithToolSearch.class)
                .streamingChatModel(spyModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .tools(spyTools)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        // when
        ChatResponse chatResponse = chat(assistant, "What is the weather and time in London?", instructions);

        // then
        assertThat(chatResponse.aiMessage().text().toLowerCase()).contains("sun", "12", "34");

        InOrder inOrder = inOrder(spyModel, spyToolSearchStrategy, spyTools);

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, "tool_search_tool")
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "weather")
        ));
        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "time")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")

                        && request.messages().size() == 5
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals("tool_search_tool")
                        && toolResultMessage.text().equals("Tools found: getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getWeather"))
                        && request.messages().get(4) instanceof ToolExecutionResultMessage toolResultMessage2
                        && toolResultMessage2.toolName().equals("tool_search_tool")
                        && toolResultMessage2.text().equals("Tools found: getTime")
                        && toolResultMessage2.attributes().get("found_tools").equals(List.of("getTime"))
        ), any());

        inOrder.verify(spyTools).getWeather("London");
        inOrder.verify(spyTools).getTime("London");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        verifyNoMoreInteractions(spyTools);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__multiple_simultaneous_tool_searches__tool_provider(StreamingChatModel model) throws Exception {

        // given
        ToolProvider toolProvider = new AiServicesWithToolSearchToolIT.SearchableTools.SearchableToolsProvider();
        StreamingChatModel spyModel = spy(model);
        ToolSearchStrategy spyToolSearchStrategy = spy(new SimpleToolSearchStrategy());

        String instructions = """
                Use separate tool calls for separate search terms.
                For example, when asked "What is the weather and time in London?",
                call 'tool_search_tool' twice (simultaneously, in parallel),
                once with ["London", "weather"] arguments, once with ["London", "time"] arguments.
                """;

        AssistantWithToolSearch assistant = AiServices.builder(AssistantWithToolSearch.class)
                .streamingChatModel(spyModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .toolProvider(toolProvider)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        // when
        ChatResponse chatResponse = chat(assistant, "What is the weather and time in London?", instructions);

        // then
        assertThat(chatResponse.aiMessage().text().toLowerCase()).contains("sun", "12", "34");

        InOrder inOrder = inOrder(spyModel, spyToolSearchStrategy);

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, "tool_search_tool")
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "weather")
        ));
        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "time")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")

                        && request.messages().size() == 5
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals("tool_search_tool")
                        && toolResultMessage.text().equals("Tools found: getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getWeather"))
                        && request.messages().get(4) instanceof ToolExecutionResultMessage toolResultMessage2
                        && toolResultMessage2.toolName().equals("tool_search_tool")
                        && toolResultMessage2.text().equals("Tools found: getTime")
                        && toolResultMessage2.attributes().get("found_tools").equals(List.of("getTime"))
        ), any());

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__multiple_simultaneous_tool_searches__overlapping_tools(StreamingChatModel model) throws Exception {

        // given
        StreamingChatModel spyModel = spy(model);
        AiServicesWithToolSearchToolIT.SearchableTools spyTools = spy(new AiServicesWithToolSearchToolIT.SearchableTools());
        ToolSearchStrategy spyToolSearchStrategy = spy(new ToolSearchStrategy() {

            @Override
            public List<ToolSpecification> getToolSearchTools(InvocationContext context) {
                return List.of(
                        ToolSpecification.builder()
                                .name("tool_search_tool")
                                .description("Finds available tools whose name or description contains given search terms")
                                .parameters(JsonObjectSchema.builder()
                                        .addProperty("terms", JsonArraySchema.builder()
                                                .description("A list of search terms used to find relevant tools")
                                                .items(new JsonStringSchema())
                                                .build())
                                        .required("terms")
                                        .build())
                                .build()
                );
            }

            @Override
            public ToolSearchResult search(ToolSearchRequest request) {
                // find all available tools
                List<String> foundToolNames = request.searchableTools().stream().map(it -> it.name()).toList();
                return new ToolSearchResult(foundToolNames, "Tools found: " + String.join(", ", foundToolNames));
            }
        });

        String instructions = """
                Use separate tool calls for separate search terms.
                For example, when asked "What is the weather and time in London?",
                call 'tool_search_tool' twice (simultaneously, in parallel),
                once with ["London", "weather"] arguments, once with ["London", "time"] arguments.
                """;

        AssistantWithToolSearch assistant = AiServices.builder(AssistantWithToolSearch.class)
                .streamingChatModel(spyModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .tools(spyTools)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        // when
        ChatResponse chatResponse = chat(assistant, "What is the weather and time in London?", instructions);

        // then
        assertThat(chatResponse.aiMessage().text().toLowerCase()).contains("sun", "12", "34");

        InOrder inOrder = inOrder(spyModel, spyToolSearchStrategy, spyTools);

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, "tool_search_tool")
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "weather")
        ));
        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "time")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")

                        && request.messages().size() == 5
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals("tool_search_tool")
                        && toolResultMessage.text().equals("Tools found: getTime, getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getTime", "getWeather"))
                        && request.messages().get(4) instanceof ToolExecutionResultMessage toolResultMessage2
                        && toolResultMessage2.toolName().equals("tool_search_tool")
                        && toolResultMessage2.text().equals("Tools found: getTime, getWeather")
                        && toolResultMessage2.attributes().get("found_tools").equals(List.of("getTime", "getWeather"))
        ), any());

        inOrder.verify(spyTools).getWeather("London");
        inOrder.verify(spyTools).getTime("London");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        verifyNoMoreInteractions(spyTools);

        // when
        ChatResponse chatResponse2 = chat(assistant, "Search for 'getDate' tool", instructions);

        // then
        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "getDate")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")

                        && request.messages().size() == 12
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals("tool_search_tool")
                        && toolResultMessage.text().equals("Tools found: getTime, getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getTime", "getWeather"))
                        && request.messages().get(4) instanceof ToolExecutionResultMessage toolResultMessage2
                        && toolResultMessage2.toolName().equals("tool_search_tool")
                        && toolResultMessage2.text().equals("Tools found: getTime, getWeather")
                        && toolResultMessage2.attributes().get("found_tools").equals(List.of("getTime", "getWeather"))
                        && request.messages().get(11) instanceof ToolExecutionResultMessage toolResultMessage3
                        && toolResultMessage3.toolName().equals("tool_search_tool")
                        && toolResultMessage3.text().equals("Tools found: ")
                        && toolResultMessage3.attributes().get("found_tools").equals(List.of())
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        verifyNoMoreInteractions(spyTools);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__simultaneous_tool_search_and_normal_tool_call(StreamingChatModel model) throws Exception {

        // given
        StreamingChatModel spyModel = spy(model);
        AiServicesWithToolSearchToolIT.SearchableTools spyTools = spy(new AiServicesWithToolSearchToolIT.SearchableTools());
        ToolSearchStrategy spyToolSearchStrategy = spy(new SimpleToolSearchStrategy());

        AssistantWithToolSearch assistant = AiServices.builder(AssistantWithToolSearch.class)
                .streamingChatModel(spyModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .tools(spyTools)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        // when
        ChatResponse chatResponse = chat(assistant, "What is the weather in London?");

        // then
        assertThat(chatResponse.aiMessage().text().toLowerCase()).contains("sun");

        InOrder inOrder = inOrder(spyModel, spyToolSearchStrategy, spyTools);

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, "tool_search_tool")
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "weather")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")

                        && request.messages().size() == 4
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals("tool_search_tool")
                        && toolResultMessage.text().equals("Tools found: getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getWeather"))
        ), any());

        inOrder.verify(spyTools).getWeather("London");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        verifyNoMoreInteractions(spyTools);

        // when
        ChatResponse chatResponse2 = chat(assistant, "What is the time in London? What is the weather in Paris? " +
                "Call 2 tools simultaneously (in parallel), in this order: tool_search_tool, getWeather");

        // then
        assertThat(chatResponse2.aiMessage().text().toLowerCase()).contains("12", "34", "rain");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "time")
        ));
        inOrder.verify(spyTools).getWeather("Paris");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")

                        && request.messages().size() == 11
                        && request.messages().get(9) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals("tool_search_tool")
                        && toolResultMessage.text().equals("Tools found: getTime")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getTime"))
                        && request.messages().get(10) instanceof ToolExecutionResultMessage toolResultMessage2
                        && toolResultMessage2.toolName().equals("getWeather")
                        && toolResultMessage2.text().equals("rainy")
        ), any());

        inOrder.verify(spyTools).getTime("London");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        verifyNoMoreInteractions(spyTools);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__simultaneous_tool_search_and_normal_tool_call__tool_provider(StreamingChatModel model) throws Exception {

        // given
        ToolProvider toolProvider = new AiServicesWithToolSearchToolIT.SearchableTools.SearchableToolsProvider();
        StreamingChatModel spyModel = spy(model);
        ToolSearchStrategy spyToolSearchStrategy = spy(new SimpleToolSearchStrategy());

        AssistantWithToolSearch assistant = AiServices.builder(AssistantWithToolSearch.class)
                .streamingChatModel(spyModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .toolProvider(toolProvider)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        // when
        ChatResponse chatResponse = chat(assistant, "What is the weather in London?");

        // then
        assertThat(chatResponse.aiMessage().text().toLowerCase()).contains("sun");

        InOrder inOrder = inOrder(spyModel, spyToolSearchStrategy);

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, "tool_search_tool")
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "weather")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")

                        && request.messages().size() == 4
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals("tool_search_tool")
                        && toolResultMessage.text().equals("Tools found: getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getWeather"))
        ), any());

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);

        // when
        ChatResponse chatResponse2 = chat(assistant, "What is the time in London? What is the weather in Paris? " +
                "Call 2 tools simultaneously (in parallel), in this order: tool_search_tool, getWeather");

        // then
        assertThat(chatResponse2.aiMessage().text().toLowerCase()).contains("12", "34", "rain");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "time")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")

                        && request.messages().size() == 11
                        && request.messages().get(9) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals("tool_search_tool")
                        && toolResultMessage.text().equals("Tools found: getTime")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getTime"))
                        && request.messages().get(10) instanceof ToolExecutionResultMessage toolResultMessage2
                        && toolResultMessage2.toolName().equals("getWeather")
                        && toolResultMessage2.text().equals("rainy")
        ), any());

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool_with_always_visible_tools(StreamingChatModel model) throws Exception {

        // given
        StreamingChatModel spyModel = spy(model);
        AiServicesWithToolSearchToolIT.AlwaysVisibleTools spyAlwaysVisibleTools = spy(new AiServicesWithToolSearchToolIT.AlwaysVisibleTools());
        AiServicesWithToolSearchToolIT.SearchableTools spySearchableTools = spy(new AiServicesWithToolSearchToolIT.SearchableTools());
        ToolSearchStrategy spyToolSearchStrategy = spy(new SimpleToolSearchStrategy());

        AssistantWithToolSearch assistant = AiServices.builder(AssistantWithToolSearch.class)
                .streamingChatModel(spyModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .tools(spyAlwaysVisibleTools, spySearchableTools)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        // when
        ChatResponse chatResponse = chat(assistant, "What is the weather in London?");

        // then
        assertThat(chatResponse.aiMessage().text().toLowerCase()).contains("sun");

        InOrder inOrder = inOrder(spyModel, spyToolSearchStrategy, spyAlwaysVisibleTools, spySearchableTools);

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "add")
                        && containsTool(request, "tool_search_tool")
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "weather")
                        && hasSearchableTools(request, "getWeather", "getTime")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "add")
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")

                        && request.messages().size() == 4
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals("tool_search_tool")
                        && toolResultMessage.text().equals("Tools found: getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getWeather"))
        ), any());

        inOrder.verify(spySearchableTools).getWeather("London");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "add")
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        verifyNoInteractions(spyAlwaysVisibleTools);
        verifyNoMoreInteractions(spySearchableTools);

        // when
        ChatResponse chatResponse2 = chat(assistant, "What is the time in London?");

        // then
        assertThat(chatResponse2.aiMessage().text()).contains("12", "34");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "add")
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "time")
                        && hasSearchableTools(request, "getTime")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 4
                        && containsTool(request, "add")
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        inOrder.verify(spySearchableTools).getTime("London");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 4
                        && containsTool(request, "add")
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        verifyNoInteractions(spyAlwaysVisibleTools);
        verifyNoMoreInteractions(spySearchableTools);

        // when
        ChatResponse chatResponse3 = chat(assistant, "How much is 2+2? Use one of the tools!");

        // then
        assertThat(chatResponse3.aiMessage().text()).contains("21");

        verify(spyAlwaysVisibleTools).add(2, 2);
        verifyNoMoreInteractions(spyAlwaysVisibleTools);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool_with_always_visible_tools__tool_provider(StreamingChatModel model) throws Exception {

        // given
        ToolProvider toolProvider = new AiServicesWithToolSearchToolIT.SearchableTools.SearchableToolsProvider() {

            @Override
            public ToolProviderResult provideTools(ToolProviderRequest request) {

                ToolSpecification addTool = ToolSpecification.builder()
                        .name("add")
                        .parameters(JsonObjectSchema.builder()
                                .addIntegerProperty("a")
                                .addIntegerProperty("b")
                                .required("a", "b")
                                .build())
                        .metadata(Map.of("searchBehavior", SearchBehavior.ALWAYS_VISIBLE))
                        .build();

                return super.provideTools(request).toBuilder()
                        .add(addTool, new ToolExecutor() {
                            @Override
                            public String execute(ToolExecutionRequest request, Object memoryId) {
                                record Args(int a, int b) {
                                }
                                Args args = Json.fromJson(request.arguments(), Args.class);
                                return String.valueOf(args.a + args.b + 17);
                            }
                        })
                        .build();
            }
        };
        StreamingChatModel spyModel = spy(model);
        ToolSearchStrategy spyToolSearchStrategy = spy(new SimpleToolSearchStrategy());

        AssistantWithToolSearch assistant = AiServices.builder(AssistantWithToolSearch.class)
                .streamingChatModel(spyModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .toolProvider(toolProvider)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        // when
        ChatResponse chatResponse = chat(assistant, "What is the weather in London?");

        // then
        assertThat(chatResponse.aiMessage().text().toLowerCase()).contains("sun");

        InOrder inOrder = inOrder(spyModel, spyToolSearchStrategy);

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "add")
                        && containsTool(request, "tool_search_tool")
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "weather")
                        && hasSearchableTools(request, "getWeather", "getTime")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "add")
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")

                        && request.messages().size() == 4
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals("tool_search_tool")
                        && toolResultMessage.text().equals("Tools found: getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getWeather"))
        ), any());

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "add")
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);

        // when
        ChatResponse chatResponse2 = chat(assistant, "What is the time in London?");

        // then
        assertThat(chatResponse2.aiMessage().text()).contains("12", "34");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "add")
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "time")
                        && hasSearchableTools(request, "getTime")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 4
                        && containsTool(request, "add")
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 4
                        && containsTool(request, "add")
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);

        // when
        ChatResponse chatResponse3 = chat(assistant, "How much is 2+2? Use one of the tools!");

        // then
        assertThat(chatResponse3.aiMessage().text()).contains("21");
    }

    private static ChatResponse chat(AssistantWithToolSearch assistant, String userMessage) throws Exception {
        return chat(assistant, userMessage, new TestTokenStreamHandler());
    }

    private static ChatResponse chat(AssistantWithToolSearch assistant,
                                     String userMessage,
                                     TestTokenStreamHandler handler) throws Exception {
        return chat(assistant, userMessage, "", handler);
    }

    private static ChatResponse chat(AssistantWithToolSearch assistant,
                                     String userMessage,
                                     String instructions) throws Exception {
        return chat(assistant, userMessage, instructions, new TestTokenStreamHandler());
    }

    private static ChatResponse chat(AssistantWithToolSearch assistant,
                                     String userMessage,
                                     String instructions,
                                     TestTokenStreamHandler handler) throws Exception {
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
        assistant
                .chat(userMessage, instructions)
                .beforeToolExecution(handler::beforeToolExecution)
                .onToolExecuted(handler::onToolExecuted)
                .onCompleteResponse(completeResponse -> {
                    handler.onCompleteResponse(completeResponse);
                    futureResponse.complete(completeResponse);
                })
                .onError(error -> {
                    handler.onError(error);
                    futureResponse.completeExceptionally(error);
                })
                .start();
        return futureResponse.get(60, SECONDS);
    }

    private static void verifyNoMoreImportantInteractions(StreamingChatModel model) {
        ignoreInteractions(model).doChat(any(), any());
        ignoreInteractions(model).defaultRequestParameters();
        ignoreInteractions(model).supportedCapabilities();
        ignoreInteractions(model).listeners();
        ignoreInteractions(model).provider();
        verifyNoMoreInteractions(model);
    }
}
