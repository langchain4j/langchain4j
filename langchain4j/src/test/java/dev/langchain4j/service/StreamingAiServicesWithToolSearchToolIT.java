package dev.langchain4j.service;

import dev.langchain4j.LoggingChatModelListener;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.ToolSearchResult;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static dev.langchain4j.MockitoUtils.ignoreInteractions;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.service.AiServicesWithToolSearchToolIT.CustomToolSearchStrategy.TOOL_SEARCH_TOOL;
import static dev.langchain4j.service.AiServicesWithToolSearchToolIT.CustomToolSearchStrategy.TOOL_SEARCH_TOOL_NAME;
import static dev.langchain4j.service.AiServicesWithToolSearchToolIT.containsTool;
import static dev.langchain4j.service.AiServicesWithToolSearchToolIT.hasToolSearch;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
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
        AiServicesWithToolSearchToolIT.LazyTools spyTools = spy(new AiServicesWithToolSearchToolIT.LazyTools());
        ToolSearchStrategy spyToolSearchStrategy = spy(new AiServicesWithToolSearchToolIT.CustomToolSearchStrategy());
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
                        && containsTool(request, TOOL_SEARCH_TOOL)
        ), any());

        inOrder.verify(spyBeforeToolExecution).accept(argThat(bte ->
                bte.request().name().equals(TOOL_SEARCH_TOOL_NAME)
                        && bte.request().arguments().contains("weather")
        ));
        inOrder.verify(handler).beforeToolExecution(argThat(bte ->
                bte.request().name().equals(TOOL_SEARCH_TOOL_NAME)
                        && bte.request().arguments().contains("weather")
        ));

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, TOOL_SEARCH_TOOL, "weather")
        ));

        inOrder.verify(spyAfterToolExecution).accept(argThat(te ->
                te.request().name().equals(TOOL_SEARCH_TOOL_NAME)
                        && te.result().equals("Tools found: getWeather")
        ));
        inOrder.verify(handler).onToolExecuted(argThat(te ->
                te.request().name().equals(TOOL_SEARCH_TOOL_NAME)
                        && te.result().equals("Tools found: getWeather")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")

                        && request.messages().size() == 4
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals(TOOL_SEARCH_TOOL_NAME)
                        && toolResultMessage.text().equals("Tools found: getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getWeather"))
        ), any());

        inOrder.verify(spyTools).getWeather("London");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        ignoreInteractions(spyToolSearchStrategy).getToolSearchTools(any());
        verifyNoMoreInteractions(spyToolSearchStrategy);
        verifyNoMoreInteractions(spyTools);

        CompletableFuture<ChatResponse> futureResponse2 = new CompletableFuture<>();

        // when
        ChatResponse chatResponse2 = chat(assistant, "What is the time in London?", handler);

        // then
        assertThat(chatResponse2.aiMessage().text()).contains("12", "34");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
        ), any());

        inOrder.verify(spyBeforeToolExecution).accept(argThat(bte ->
                bte.request().name().equals(TOOL_SEARCH_TOOL_NAME)
                        && bte.request().arguments().contains("time")
        ));
        inOrder.verify(handler).beforeToolExecution(argThat(bte ->
                bte.request().name().equals(TOOL_SEARCH_TOOL_NAME)
                        && bte.request().arguments().contains("time")
        ));

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, TOOL_SEARCH_TOOL, "time")
        ));

        inOrder.verify(spyAfterToolExecution).accept(argThat(te ->
                te.request().name().equals(TOOL_SEARCH_TOOL_NAME)
                        && te.result().equals("Tools found: getTime")
        ));
        inOrder.verify(handler).onToolExecuted(argThat(te ->
                te.request().name().equals(TOOL_SEARCH_TOOL_NAME)
                        && te.result().equals("Tools found: getTime")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        inOrder.verify(spyTools).getTime("London");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        ignoreInteractions(spyToolSearchStrategy).getToolSearchTools(any());
        verifyNoMoreInteractions(spyToolSearchStrategy);
        verifyNoMoreInteractions(spyTools);
    }

    private static ChatResponse chat(AssistantWithToolSearch assistant,
                                     String userMessage,

                                     TestTokenStreamHandler handler) throws Exception {
        return chat(assistant, userMessage, "", handler);
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

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__keep_previously_found_tools_in_chat_memory__tool_provider(StreamingChatModel model) throws Exception {

        // given
        ToolProvider toolProvider = AiServicesWithToolSearchToolIT.LazyTools.TOOL_PROVIDER;
        StreamingChatModel spyModel = spy(model);
        ToolSearchStrategy spyToolSearchStrategy = spy(new AiServicesWithToolSearchToolIT.CustomToolSearchStrategy());

        AssistantWithToolSearch assistant = AiServices.builder(AssistantWithToolSearch.class)
                .streamingChatModel(spyModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .toolProvider(toolProvider)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        TestTokenStreamHandler handler = spy(TestTokenStreamHandler.class);

        // when
        ChatResponse chatResponse = chat(assistant, "What is the weather in London?", handler);

        // then
        assertThat(chatResponse.aiMessage().text().toLowerCase()).contains("sun");

        InOrder inOrder = inOrder(spyModel, spyToolSearchStrategy);

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, TOOL_SEARCH_TOOL)
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, TOOL_SEARCH_TOOL, "weather")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")

                        && request.messages().size() == 4
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals(TOOL_SEARCH_TOOL_NAME)
                        && toolResultMessage.text().equals("Tools found: getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getWeather"))
        ), any());

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        ignoreInteractions(spyToolSearchStrategy).getToolSearchTools(any());
        verifyNoMoreInteractions(spyToolSearchStrategy);

        // when
        ChatResponse chatResponse2 = chat(assistant, "What is the time in London?", handler);

        // then
        assertThat(chatResponse2.aiMessage().text()).contains("12", "34");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, TOOL_SEARCH_TOOL, "time")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        ignoreInteractions(spyToolSearchStrategy).getToolSearchTools(any());
        verifyNoMoreInteractions(spyToolSearchStrategy);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__multiple_simultaneous_tool_searches(StreamingChatModel model) throws Exception {

        // given
        StreamingChatModel spyModel = spy(model);
        AiServicesWithToolSearchToolIT.LazyTools spyTools = spy(new AiServicesWithToolSearchToolIT.LazyTools());
        ToolSearchStrategy spyToolSearchStrategy = spy(new AiServicesWithToolSearchToolIT.CustomToolSearchStrategy());

        String instructions = """
                Use separate tool calls for separate search terms.
                For example, when asked "What is the weather and time in London?",
                call 'tool_search_tool' twice (simultaneously, in parallel),
                once with "London weather" argument, once with "London time" argument.
                """;

        AssistantWithToolSearch assistant = AiServices.builder(AssistantWithToolSearch.class)
                .streamingChatModel(spyModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .tools(spyTools)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        TestTokenStreamHandler handler = spy(TestTokenStreamHandler.class);

        // when
        ChatResponse chatResponse = chat(assistant, "What is the weather and time in London?", instructions, handler);

        // then
        assertThat(chatResponse.aiMessage().text().toLowerCase()).contains("sun", "12", "34");

        InOrder inOrder = inOrder(spyModel, spyToolSearchStrategy, spyTools);

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, TOOL_SEARCH_TOOL)
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, TOOL_SEARCH_TOOL, "weather")
        ));
        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, TOOL_SEARCH_TOOL, "time")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")

                        && request.messages().size() == 5
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals(TOOL_SEARCH_TOOL_NAME)
                        && toolResultMessage.text().equals("Tools found: getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getWeather"))
                        && request.messages().get(4) instanceof ToolExecutionResultMessage toolResultMessage2
                        && toolResultMessage2.toolName().equals(TOOL_SEARCH_TOOL_NAME)
                        && toolResultMessage2.text().equals("Tools found: getTime")
                        && toolResultMessage2.attributes().get("found_tools").equals(List.of("getTime"))
        ), any());

        inOrder.verify(spyTools).getWeather("London");
        inOrder.verify(spyTools).getTime("London");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        ignoreInteractions(spyToolSearchStrategy).getToolSearchTools(any());
        verifyNoMoreInteractions(spyToolSearchStrategy);
        verifyNoMoreInteractions(spyTools);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__multiple_simultaneous_tool_searches__tool_provider(StreamingChatModel model) throws Exception {

        // given
        ToolProvider toolProvider = AiServicesWithToolSearchToolIT.LazyTools.TOOL_PROVIDER;
        StreamingChatModel spyModel = spy(model);
        ToolSearchStrategy spyToolSearchStrategy = spy(new AiServicesWithToolSearchToolIT.CustomToolSearchStrategy());

        String instructions = """
                Use separate tool calls for separate search terms.
                For example, when asked "What is the weather and time in London?",
                call 'tool_search_tool' twice (simultaneously, in parallel),
                once with "London weather" argument, once with "London time" argument.
                """;

        AssistantWithToolSearch assistant = AiServices.builder(AssistantWithToolSearch.class)
                .streamingChatModel(spyModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .toolProvider(toolProvider)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        TestTokenStreamHandler handler = spy(TestTokenStreamHandler.class);

        // when
        ChatResponse chatResponse = chat(assistant, "What is the weather and time in London?", instructions, handler);

        // then
        assertThat(chatResponse.aiMessage().text().toLowerCase()).contains("sun", "12", "34");

        InOrder inOrder = inOrder(spyModel, spyToolSearchStrategy);

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, TOOL_SEARCH_TOOL)
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, TOOL_SEARCH_TOOL, "weather")
        ));
        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, TOOL_SEARCH_TOOL, "time")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")

                        && request.messages().size() == 5
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals(TOOL_SEARCH_TOOL_NAME)
                        && toolResultMessage.text().equals("Tools found: getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getWeather"))
                        && request.messages().get(4) instanceof ToolExecutionResultMessage toolResultMessage2
                        && toolResultMessage2.toolName().equals(TOOL_SEARCH_TOOL_NAME)
                        && toolResultMessage2.text().equals("Tools found: getTime")
                        && toolResultMessage2.attributes().get("found_tools").equals(List.of("getTime"))
        ), any());

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        ignoreInteractions(spyToolSearchStrategy).getToolSearchTools(any());
        verifyNoMoreInteractions(spyToolSearchStrategy);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__multiple_simultaneous_tool_searches__overlapping_tools(StreamingChatModel model) throws Exception {

        // given
        StreamingChatModel spyModel = spy(model);
        AiServicesWithToolSearchToolIT.LazyTools spyTools = spy(new AiServicesWithToolSearchToolIT.LazyTools());
        ToolSearchStrategy spyToolSearchStrategy = spy(new ToolSearchStrategy() {

            @Override
            public List<ToolSpecification> getToolSearchTools(InvocationContext context) {
                return List.of(TOOL_SEARCH_TOOL);
            }

            @Override
            public ToolSearchResult search(ToolSearchRequest request) {
                // find all available tools
                List<String> foundToolNames = request.availableTools().stream().map(it -> it.name()).toList();
                return new ToolSearchResult(foundToolNames);
            }
        });

        String instructions = """
                Use separate tool calls for separate search terms.
                For example, when asked "What is the weather and time in London?",
                call 'tool_search_tool' twice (simultaneously, in parallel),
                once with "London weather" argument, once with "London time" argument.
                """;

        AssistantWithToolSearch assistant = AiServices.builder(AssistantWithToolSearch.class)
                .streamingChatModel(spyModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .tools(spyTools)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        TestTokenStreamHandler handler = spy(TestTokenStreamHandler.class);

        // when
        ChatResponse chatResponse = chat(assistant, "What is the weather and time in London?", instructions, handler);

        // then
        assertThat(chatResponse.aiMessage().text().toLowerCase()).contains("sun", "12", "34");

        InOrder inOrder = inOrder(spyModel, spyToolSearchStrategy, spyTools);

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, TOOL_SEARCH_TOOL)
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, TOOL_SEARCH_TOOL, "weather")
        ));
        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, TOOL_SEARCH_TOOL, "time")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 4
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
                        && containsTool(request, "add")

                        && request.messages().size() == 5
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals(TOOL_SEARCH_TOOL_NAME)
                        && toolResultMessage.text().equals("Tools found: add, getTime, getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("add", "getTime", "getWeather"))
                        && request.messages().get(4) instanceof ToolExecutionResultMessage toolResultMessage2
                        && toolResultMessage2.toolName().equals(TOOL_SEARCH_TOOL_NAME)
                        && toolResultMessage2.text().equals("Tools found: add, getTime, getWeather")
                        && toolResultMessage2.attributes().get("found_tools").equals(List.of("add", "getTime", "getWeather"))
        ), any());

        inOrder.verify(spyTools).getWeather("London");
        inOrder.verify(spyTools).getTime("London");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 4
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
                        && containsTool(request, "add")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        ignoreInteractions(spyToolSearchStrategy).getToolSearchTools(any());
        verifyNoMoreInteractions(spyToolSearchStrategy);
        verifyNoMoreInteractions(spyTools);

        // when
        ChatResponse chatResponse2 = chat(assistant, "Search for 'getDate' tool", instructions, handler);

        // then
        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 4
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
                        && containsTool(request, "add")
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, TOOL_SEARCH_TOOL, "getDate")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 4
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
                        && containsTool(request, "add")

                        && request.messages().size() == 12
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals(TOOL_SEARCH_TOOL_NAME)
                        && toolResultMessage.text().equals("Tools found: add, getTime, getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("add", "getTime", "getWeather"))
                        && request.messages().get(4) instanceof ToolExecutionResultMessage toolResultMessage2
                        && toolResultMessage2.toolName().equals(TOOL_SEARCH_TOOL_NAME)
                        && toolResultMessage2.text().equals("Tools found: add, getTime, getWeather")
                        && toolResultMessage2.attributes().get("found_tools").equals(List.of("add", "getTime", "getWeather"))
                        && request.messages().get(11) instanceof ToolExecutionResultMessage toolResultMessage3
                        && toolResultMessage3.toolName().equals(TOOL_SEARCH_TOOL_NAME)
                        && toolResultMessage3.text().equals("Tools found: add, getTime, getWeather")
                        && toolResultMessage3.attributes().get("found_tools").equals(List.of("add", "getTime", "getWeather"))
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        ignoreInteractions(spyToolSearchStrategy).getToolSearchTools(any());
        verifyNoMoreInteractions(spyToolSearchStrategy);
        verifyNoMoreInteractions(spyTools);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__simultaneous_tool_search_and_normal_tool_call(StreamingChatModel model) throws Exception {

        // given
        StreamingChatModel spyModel = spy(model);
        AiServicesWithToolSearchToolIT.LazyTools spyTools = spy(new AiServicesWithToolSearchToolIT.LazyTools());
        ToolSearchStrategy spyToolSearchStrategy = spy(new AiServicesWithToolSearchToolIT.CustomToolSearchStrategy());

        AssistantWithToolSearch assistant = AiServices.builder(AssistantWithToolSearch.class)
                .streamingChatModel(spyModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .tools(spyTools)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        TestTokenStreamHandler handler = spy(TestTokenStreamHandler.class);

        // when
        ChatResponse chatResponse = chat(assistant, "What is the weather in London?", handler);

        // then
        assertThat(chatResponse.aiMessage().text().toLowerCase()).contains("sun");

        InOrder inOrder = inOrder(spyModel, spyToolSearchStrategy, spyTools);

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, TOOL_SEARCH_TOOL)
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, TOOL_SEARCH_TOOL, "weather")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")

                        && request.messages().size() == 4
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals(TOOL_SEARCH_TOOL_NAME)
                        && toolResultMessage.text().equals("Tools found: getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getWeather"))
        ), any());

        inOrder.verify(spyTools).getWeather("London");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        ignoreInteractions(spyToolSearchStrategy).getToolSearchTools(any());
        verifyNoMoreInteractions(spyToolSearchStrategy);
        verifyNoMoreInteractions(spyTools);

        // when
        ChatResponse chatResponse2 = chat(assistant, "What is the time in London? What is the weather in Paris? " +
                "Call 2 tools simultaneously (in parallel), in this order: tool_search_tool, getWeather", handler);

        // then
        assertThat(chatResponse2.aiMessage().text().toLowerCase()).contains("12", "34", "rain");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, TOOL_SEARCH_TOOL, "time")
        ));
        inOrder.verify(spyTools).getWeather("Paris");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")

                        && request.messages().size() == 11
                        && request.messages().get(9) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals(TOOL_SEARCH_TOOL_NAME)
                        && toolResultMessage.text().equals("Tools found: getTime")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getTime"))
                        && request.messages().get(10) instanceof ToolExecutionResultMessage toolResultMessage2
                        && toolResultMessage2.toolName().equals("getWeather")
                        && toolResultMessage2.text().equals("rainy")
        ), any());

        inOrder.verify(spyTools).getTime("London");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        ignoreInteractions(spyToolSearchStrategy).getToolSearchTools(any());
        verifyNoMoreInteractions(spyToolSearchStrategy);
        verifyNoMoreInteractions(spyTools);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__simultaneous_tool_search_and_normal_tool_call__tool_provider(StreamingChatModel model) throws Exception {

        // given
        ToolProvider toolProvider = AiServicesWithToolSearchToolIT.LazyTools.TOOL_PROVIDER;
        StreamingChatModel spyModel = spy(model);
        ToolSearchStrategy spyToolSearchStrategy = spy(new AiServicesWithToolSearchToolIT.CustomToolSearchStrategy());

        AssistantWithToolSearch assistant = AiServices.builder(AssistantWithToolSearch.class)
                .streamingChatModel(spyModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .toolProvider(toolProvider)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        TestTokenStreamHandler handler = spy(TestTokenStreamHandler.class);

        // when
        ChatResponse chatResponse = chat(assistant, "What is the weather in London?", handler);

        // then
        assertThat(chatResponse.aiMessage().text().toLowerCase()).contains("sun");

        InOrder inOrder = inOrder(spyModel, spyToolSearchStrategy);

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, TOOL_SEARCH_TOOL)
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, TOOL_SEARCH_TOOL, "weather")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")

                        && request.messages().size() == 4
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals(TOOL_SEARCH_TOOL_NAME)
                        && toolResultMessage.text().equals("Tools found: getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getWeather"))
        ), any());

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        ignoreInteractions(spyToolSearchStrategy).getToolSearchTools(any());
        verifyNoMoreInteractions(spyToolSearchStrategy);

        // when
        ChatResponse chatResponse2 = chat(assistant, "What is the time in London? What is the weather in Paris? " +
                "Call 2 tools simultaneously (in parallel), in this order: tool_search_tool, getWeather", handler);

        // then
        assertThat(chatResponse2.aiMessage().text().toLowerCase()).contains("12", "34", "rain");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
        ), any());

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, TOOL_SEARCH_TOOL, "time")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")

                        && request.messages().size() == 11
                        && request.messages().get(9) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals(TOOL_SEARCH_TOOL_NAME)
                        && toolResultMessage.text().equals("Tools found: getTime")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getTime"))
                        && request.messages().get(10) instanceof ToolExecutionResultMessage toolResultMessage2
                        && toolResultMessage2.toolName().equals("getWeather")
                        && toolResultMessage2.text().equals("rainy")
        ), any());

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, TOOL_SEARCH_TOOL)
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ), any());

        verifyNoMoreImportantInteractions(spyModel);
        ignoreInteractions(spyToolSearchStrategy).getToolSearchTools(any());
        verifyNoMoreInteractions(spyToolSearchStrategy);
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
