package dev.langchain4j.service;

import dev.langchain4j.LoggingChatModelListener;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.openai.OpenAiChatModel;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class AiServicesWithToolSearchToolIT {

    static List<ChatModel> models() {
        return List.of(
                OpenAiChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .modelName(GPT_4_O_MINI)
                        .temperature(0.0)
                        .listeners(new LoggingChatModelListener())
                        .build(),
                OpenAiChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .modelName(GPT_4_O_MINI)
                        .strictTools(true)
                        .temperature(0.0)
                        .listeners(new LoggingChatModelListener())
                        .build());
    }

    public static class SearchableTools {

        @Tool
        public String getWeather(String city) {
            if (city.equals("London")) {
                return "sunny";
            } else {
                return "rainy";
            }
        }

        @Tool
        public String getTime(String city) {
            return "12:34:56";
        }

        public static ToolProvider TOOL_PROVIDER = new ToolProvider() {

            @Override
            public ToolProviderResult provideTools(ToolProviderRequest request) {
                return ToolProviderResult.builder()
                        .add(toolSpecificationFrom(getMethod(SearchableTools.class, "getWeather", String.class)), new ToolExecutor() {
                            @Override
                            public String execute(ToolExecutionRequest request, Object memoryId) {
                                if (request.arguments().contains("London")) {
                                    return "sunny";
                                } else {
                                    return "rainy";
                                }
                            }
                        })
                        .add(toolSpecificationFrom(getMethod(SearchableTools.class, "getTime", String.class)), new ToolExecutor() {
                            @Override
                            public String execute(ToolExecutionRequest request, Object memoryId) {
                                return "12:34:56";
                            }
                        })
                        .build();
            }

            private static Method getMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
                try {
                    return clazz.getDeclaredMethod(name, parameterTypes);
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    public static class BeforeToolExecutionCallback implements Consumer<BeforeToolExecution> {
        public void accept(BeforeToolExecution ignored) {
        }
    }

    public static class AfterToolExecutionCallback implements Consumer<ToolExecution> {
        public void accept(ToolExecution ignored) {
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__keep_previously_found_tools_in_chat_memory(ChatModel chatModel) {

        // given
        ChatModel spyChatModel = spy(chatModel);
        SearchableTools spyTools = spy(new SearchableTools());
        ToolSearchStrategy spyToolSearchStrategy = spy(new SimpleToolSearchStrategy());
        Consumer<BeforeToolExecution> spyBeforeToolExecution = spy(new BeforeToolExecutionCallback());
        Consumer<ToolExecution> spyAfterToolExecution = spy(new AfterToolExecutionCallback());

        interface Assistant {

            @SystemMessage("Use 'tool_search_tool' tool if you need to discover other available tools")
            String chat(String userMessage);
        }

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .tools(spyTools)
                .toolSearchStrategy(spyToolSearchStrategy)
                .beforeToolExecution(spyBeforeToolExecution)
                .afterToolExecution(spyAfterToolExecution)
                .build();

        // when
        String answer = assistant.chat("What is the weather in London?");

        // then
        assertThat(answer.toLowerCase()).contains("sun");

        InOrder inOrder = inOrder(spyChatModel, spyToolSearchStrategy, spyTools, spyBeforeToolExecution, spyAfterToolExecution);

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, "tool_search_tool")
        ));

        inOrder.verify(spyBeforeToolExecution).accept(argThat(bte ->
                bte.request().name().equals("tool_search_tool")
                        && bte.request().arguments().contains("weather")
        ));

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "weather")
                        && hasAvailableTools(request, "getWeather", "getTime")
        ));

        inOrder.verify(spyAfterToolExecution).accept(argThat(te ->
                te.request().name().equals("tool_search_tool")
                        && te.result().equals("Tools found: getWeather")
        ));

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")

                        && request.messages().size() == 4
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals("tool_search_tool")
                        && toolResultMessage.text().equals("Tools found: getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getWeather"))
        ));

        inOrder.verify(spyTools).getWeather("London");

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
        verifyNoMoreInteractions(spyTools);

        // when
        String answer2 = assistant.chat("What is the time in London?");

        // then
        assertThat(answer2).contains("12", "34");

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ));

        inOrder.verify(spyBeforeToolExecution).accept(argThat(bte ->
                bte.request().name().equals("tool_search_tool")
                        && bte.request().arguments().contains("time")
        ));

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "time")
                        && hasAvailableTools(request, "getTime") // no getWeather tool, as it was found already
        ));

        inOrder.verify(spyAfterToolExecution).accept(argThat(te ->
                te.request().name().equals("tool_search_tool")
                        && te.result().equals("Tools found: getTime")
        ));

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ));

        inOrder.verify(spyTools).getTime("London");

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
        verifyNoMoreInteractions(spyTools);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__keep_previously_found_tools_in_chat_memory__tool_provider(ChatModel chatModel) {

        // given
        ToolProvider toolProvider = SearchableTools.TOOL_PROVIDER;
        ChatModel spyChatModel = spy(chatModel);
        ToolSearchStrategy spyToolSearchStrategy = spy(new SimpleToolSearchStrategy());

        interface Assistant {

            @SystemMessage("Use 'tool_search_tool' tool if you need to discover other available tools")
            String chat(String userMessage);
        }

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .toolProvider(toolProvider)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        // when
        String answer = assistant.chat("What is the weather in London?");

        // then
        assertThat(answer.toLowerCase()).contains("sun");

        InOrder inOrder = inOrder(spyChatModel, spyToolSearchStrategy);

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, "tool_search_tool")
        ));

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "weather")
        ));

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")

                        && request.messages().size() == 4
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals("tool_search_tool")
                        && toolResultMessage.text().equals("Tools found: getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getWeather"))
        ));

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);

        // when
        String answer2 = assistant.chat("What is the time in London?");

        // then
        assertThat(answer2).contains("12", "34");

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ));

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "time")
        ));

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ));

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__multiple_simultaneous_tool_searches(ChatModel chatModel) {

        // given
        ChatModel spyChatModel = spy(chatModel);
        SearchableTools spyTools = spy(new SearchableTools());
        ToolSearchStrategy spyToolSearchStrategy = spy(new SimpleToolSearchStrategy());

        interface Assistant {

            @SystemMessage("""
                    Use 'tool_search_tool' tool if you need to discover other available tools.
                    Use separate tool calls for separate search terms.
                    For example, when asked "What is the weather and time in London?",
                    call 'tool_search_tool' twice (simultaneously, in parallel),
                    once with ["London", "weather"] arguments, once with ["London", "time"] arguments.
                    """)
            String chat(String userMessage);
        }

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .tools(spyTools)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        // when
        String answer = assistant.chat("What is the weather and time in London?");

        // then
        assertThat(answer.toLowerCase()).contains("sun", "12", "34");

        InOrder inOrder = inOrder(spyChatModel, spyToolSearchStrategy, spyTools);

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, "tool_search_tool")
        ));

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "weather")
        ));
        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "time")
        ));

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
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
        ));

        inOrder.verify(spyTools).getWeather("London");
        inOrder.verify(spyTools).getTime("London");

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
        verifyNoMoreInteractions(spyTools);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__multiple_simultaneous_tool_searches__tool_provider(ChatModel chatModel) {

        // given
        ToolProvider toolProvider = SearchableTools.TOOL_PROVIDER;
        ChatModel spyChatModel = spy(chatModel);
        ToolSearchStrategy spyToolSearchStrategy = spy(new SimpleToolSearchStrategy());

        interface Assistant {

            @SystemMessage("""
                    Use 'tool_search_tool' tool if you need to discover other available tools.
                    Use separate tool calls for separate search terms.
                    For example, when asked "What is the weather and time in London?",
                    call 'tool_search_tool' twice (simultaneously, in parallel),
                    once with ["London", "weather"] arguments, once with ["London", "time"] arguments.
                    """)
            String chat(String userMessage);
        }

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .toolProvider(toolProvider)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        // when
        String answer = assistant.chat("What is the weather and time in London?");

        // then
        assertThat(answer.toLowerCase()).contains("sun", "12", "34");

        InOrder inOrder = inOrder(spyChatModel, spyToolSearchStrategy);

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, "tool_search_tool")
        ));

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "weather")
        ));
        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "time")
        ));

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
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
        ));

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__multiple_simultaneous_tool_searches__overlapping_tools(ChatModel chatModel) {

        // given
        ChatModel spyChatModel = spy(chatModel);
        SearchableTools spyTools = spy(new SearchableTools());
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

        interface Assistant {

            @SystemMessage("""
                    Use 'tool_search_tool' tool if you need to discover other available tools.
                    Use separate tool calls for separate search terms.
                    For example, when asked "What is the weather and time in London?",
                    call 'tool_search_tool' twice (simultaneously, in parallel),
                    once with ["London", "weather"] arguments, once with ["London", "time"] arguments.
                    """)
            String chat(String userMessage);
        }

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .tools(spyTools)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        // when
        String answer = assistant.chat("What is the weather and time in London?");

        // then
        assertThat(answer.toLowerCase()).contains("sun", "12", "34");

        InOrder inOrder = inOrder(spyChatModel, spyToolSearchStrategy, spyTools);

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, "tool_search_tool")
        ));

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "weather")
        ));
        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "time")
        ));

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
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
        ));

        inOrder.verify(spyTools).getWeather("London");
        inOrder.verify(spyTools).getTime("London");

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
        verifyNoMoreInteractions(spyTools);

        // when
        String answer2 = assistant.chat("Search for 'getDate' tool");

        // then
        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ));

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "getDate")
        ));

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
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
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
        verifyNoMoreInteractions(spyTools);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__simultaneous_tool_search_and_normal_tool_call(ChatModel chatModel) {

        // given
        ChatModel spyChatModel = spy(chatModel);
        SearchableTools spyTools = spy(new SearchableTools());
        ToolSearchStrategy spyToolSearchStrategy = spy(new SimpleToolSearchStrategy());

        interface Assistant {

            @SystemMessage("Use 'tool_search_tool' tool if you need to discover other available tools")
            String chat(String userMessage);
        }

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .tools(spyTools)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        // when
        String answer = assistant.chat("What is the weather in London?");

        // then
        assertThat(answer.toLowerCase()).contains("sun");

        InOrder inOrder = inOrder(spyChatModel, spyToolSearchStrategy, spyTools);

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, "tool_search_tool")
        ));

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "weather")
        ));

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")

                        && request.messages().size() == 4
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals("tool_search_tool")
                        && toolResultMessage.text().equals("Tools found: getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getWeather"))
        ));

        inOrder.verify(spyTools).getWeather("London");

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
        verifyNoMoreInteractions(spyTools);

        // when
        String answer2 = assistant.chat("What is the time in London? What is the weather in Paris? " +
                "Call 2 tools simultaneously (in parallel), in this order: tool_search_tool, getWeather");

        // then
        assertThat(answer2.toLowerCase()).contains("12", "34", "rain");

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ));

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "time")
        ));
        inOrder.verify(spyTools).getWeather("Paris");

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
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
        ));

        inOrder.verify(spyTools).getTime("London");

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
        verifyNoMoreInteractions(spyTools);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_support_tool_search_tool__simultaneous_tool_search_and_normal_tool_call__tool_provider(ChatModel chatModel) {

        // given
        ToolProvider toolProvider = SearchableTools.TOOL_PROVIDER;
        ChatModel spyChatModel = spy(chatModel);
        ToolSearchStrategy spyToolSearchStrategy = spy(new SimpleToolSearchStrategy());

        interface Assistant {

            @SystemMessage("Use 'tool_search_tool' tool if you need to discover other available tools")
            String chat(String userMessage);
        }

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .toolProvider(toolProvider)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        // when
        String answer = assistant.chat("What is the weather in London?");

        // then
        assertThat(answer.toLowerCase()).contains("sun");

        InOrder inOrder = inOrder(spyChatModel, spyToolSearchStrategy);

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, "tool_search_tool")
        ));

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "weather")
        ));

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")

                        && request.messages().size() == 4
                        && request.messages().get(3) instanceof ToolExecutionResultMessage toolResultMessage
                        && toolResultMessage.toolName().equals("tool_search_tool")
                        && toolResultMessage.text().equals("Tools found: getWeather")
                        && toolResultMessage.attributes().get("found_tools").equals(List.of("getWeather"))
        ));

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);

        // when
        String answer2 = assistant.chat("What is the time in London? What is the weather in Paris? " +
                "Call 2 tools simultaneously (in parallel), in this order: tool_search_tool, getWeather");

        // then
        assertThat(answer2.toLowerCase()).contains("12", "34", "rain");

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ));

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                hasToolSearch(request, "tool_search_tool", "time")
        ));

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
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
        ));

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
    }

    public static boolean containsTool(ChatRequest chatRequest, ToolSpecification toolSpecification) {
        return chatRequest.toolSpecifications().stream().anyMatch(t -> t.equals(toolSpecification));
    }

    public static boolean containsTool(ChatRequest chatRequest, String toolName) {
        return chatRequest.toolSpecifications().stream().anyMatch(t -> t.name().equals(toolName));
    }

    public static boolean hasToolSearch(ToolSearchRequest request, String toolName, String queryTerm) {
        return request.toolExecutionRequest().name().equals(toolName)
                && request.toolExecutionRequest().arguments().toLowerCase().contains(queryTerm.toLowerCase());
    }

    public static boolean hasAvailableTools(ToolSearchRequest request,
                                            String... toolNames) {
        return request.searchableTools().stream().map(it -> it.name()).collect(toSet())
                .equals(new HashSet<>(asList(toolNames)));
    }
}
