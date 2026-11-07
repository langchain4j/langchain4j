package dev.langchain4j.agentic;

import static dev.langchain4j.agent.tool.SearchBehavior.ALWAYS_VISIBLE;
import static dev.langchain4j.model.chat.mock.ChatModelMock.thatAlwaysResponds;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.AgenticServices.AgentConfigurator;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupplierParameterResolver;
import dev.langchain4j.agentic.declarative.ToolSearchStrategySupplier;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import dev.langchain4j.service.tool.search.simple.SimpleToolSearchStrategy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentToolSearchTest {

    private static final AtomicInteger supplierCalls = new AtomicInteger();

    @BeforeEach
    void reset() {
        supplierCalls.set(0);
    }

    @Test
    void should_discover_and_execute_static_tools() {
        ChatModelMock model = searchModel();
        Assistant agent = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(model)
                .tools(new WeatherTools())
                .toolSearchStrategy(new SimpleToolSearchStrategy())
                .build();

        assertThat(agent.answer("weather")).isEqualTo("sunny");
        assertSearch(model.requests(), "tool_search_tool");
    }

    @Test
    void should_discover_and_execute_tools_from_a_provider() {
        ChatModelMock model = searchModel();
        ToolSpecification weather = ToolSpecification.builder().name("weather").build();
        Assistant agent = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(model)
                .toolProvider(request -> ToolProviderResult.builder()
                        .add(weather, (toolRequest, memoryId) -> "sunny")
                        .build())
                .toolSearchStrategy(new SimpleToolSearchStrategy())
                .build();

        assertThat(agent.answer("weather")).isEqualTo("sunny");
        assertThat(toolNames(model.requests().get(0))).containsExactly("tool_search_tool");
        assertThat(toolNames(model.requests().get(1))).contains("weather");
        assertWeatherResult(model.requests().get(2));
    }

    @Test
    void should_discover_and_execute_tools_from_a_map() {
        ChatModelMock model = searchModel();
        ToolSpecification weather = ToolSpecification.builder().name("weather").build();
        Assistant agent = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(model)
                .tools(Map.of(weather, (request, memoryId) -> "sunny"))
                .toolSearchStrategy(new SimpleToolSearchStrategy())
                .build();

        assertThat(agent.answer("weather")).isEqualTo("sunny");
        assertThat(toolNames(model.requests().get(0))).containsExactly("tool_search_tool");
        assertThat(toolNames(model.requests().get(1))).contains("weather");
        assertWeatherResult(model.requests().get(2));
    }

    @Test
    void should_keep_all_tools_visible_when_search_is_not_configured() {
        ChatModelMock model = new ChatModelMock("sunny");
        Assistant agent = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(model)
                .tools(new WeatherTools())
                .build();

        assertThat(agent.answer("weather")).isEqualTo("sunny");
        assertThat(toolNames(model.request())).containsExactlyInAnyOrder("weather", "ping");
    }

    @Test
    void should_discover_and_execute_tools_with_an_untyped_agent() {
        ChatModelMock model = searchModel();
        UntypedAgent agent = AgenticServices.agentBuilder()
                .chatModel(model)
                .userMessage("{{request}}")
                .outputKey("answer")
                .tools(new WeatherTools())
                .toolSearchStrategy(new SimpleToolSearchStrategy())
                .build();

        assertThat(agent.invoke(Map.of("request", "weather"))).isEqualTo("sunny");
        assertSearch(model.requests(), "tool_search_tool");
    }

    @Test
    void should_configure_search_once_for_a_declarative_agent() {
        ChatModelMock model = searchModel();
        DeclarativeAssistant agent = AgenticServices.createAgenticSystem(DeclarativeAssistant.class, model);

        assertThat(agent.answer("weather")).isEqualTo("sunny");
        assertSearch(model.requests(), "tool_search_tool");
        assertThat(supplierCalls).hasValue(1);
    }

    @Test
    void should_configure_search_once_for_a_declarative_subagent() {
        ChatModelMock model = searchModel();
        WeatherWorkflow workflow = AgenticServices.createAgenticSystem(WeatherWorkflow.class, model);

        assertThat(workflow.answer("weather")).isEqualTo("sunny");
        assertSearch(model.requests(), "tool_search_tool");
        assertThat(supplierCalls).hasValue(1);
    }

    @Test
    void should_allow_the_builder_to_override_the_declarative_strategy() {
        ChatModelMock model = thatAlwaysResponds(searchCall("discover"), weatherCall(), AiMessage.from("sunny"));
        DeclarativeAssistant agent = AgenticServices.agentBuilder(DeclarativeAssistant.class)
                .chatModel(model)
                .toolSearchStrategy(
                        SimpleToolSearchStrategy.builder().toolName("discover").build())
                .build();

        assertThat(agent.answer("weather")).isEqualTo("sunny");
        assertSearch(model.requests(), "discover");
    }

    @Test
    void should_configure_search_through_an_agent_configurator() {
        ChatModelMock model = searchModel();
        ConfiguredWorkflow workflow =
                AgenticServices.createAgenticSystem(ConfiguredWorkflow.class, model, new AgentConfigurator(ctx -> {
                    if (ctx.agentServiceClass() == ConfiguredAssistant.class) {
                        ctx.agentBuilder().toolSearchStrategy(new SimpleToolSearchStrategy());
                    }
                }));

        assertThat(workflow.answer("weather")).isEqualTo("sunny");
        assertSearch(model.requests(), "tool_search_tool");
    }

    @Test
    void should_search_again_when_an_agent_with_scope_dependent_context_is_invoked_again() {
        ChatModelMock model = thatAlwaysResponds(
                searchCall("tool_search_tool"),
                weatherCall(),
                AiMessage.from("sunny"),
                searchCall("tool_search_tool"),
                weatherCall(),
                AiMessage.from("sunny"));
        Assistant agent = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(model)
                .tools(new WeatherTools())
                .toolSearchStrategy(new SimpleToolSearchStrategy())
                .context(scope -> "Use the weather tool.")
                .build();
        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(agent)
                .outputKey("answer")
                .build();

        assertThat(workflow.invoke(Map.of("request", "weather"))).isEqualTo("sunny");
        assertThat(workflow.invoke(Map.of("request", "weather"))).isEqualTo("sunny");
        assertSearch(model.requests().subList(0, 3), "tool_search_tool");
        assertSearch(model.requests().subList(3, 6), "tool_search_tool");
    }

    @Test
    void should_retain_discovered_tools_only_in_their_chat_memory() {
        ChatModelMock model = thatAlwaysResponds(
                searchCall("tool_search_tool"),
                weatherCall(),
                AiMessage.from("sunny"),
                AiMessage.from("sunny"),
                AiMessage.from("sunny"));
        MemoryAssistant agent = AgenticServices.agentBuilder(MemoryAssistant.class)
                .chatModel(model)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(20))
                .tools(new WeatherTools())
                .toolSearchStrategy(new SimpleToolSearchStrategy())
                .build();

        assertThat(agent.answer("first", "weather")).isEqualTo("sunny");
        assertThat(agent.answer("first", "weather again")).isEqualTo("sunny");
        assertThat(agent.answer("second", "weather")).isEqualTo("sunny");
        assertSearch(model.requests().subList(0, 3), "tool_search_tool");
        assertThat(toolNames(model.requests().get(3))).contains("weather");
        assertThat(toolNames(model.requests().get(4))).containsExactlyInAnyOrder("tool_search_tool", "ping");
    }

    @Test
    void should_discover_and_execute_tools_while_streaming() throws Exception {
        StreamingChatModelMock model = new StreamingChatModelMock(
                List.of(searchCall("tool_search_tool"), weatherCall(), AiMessage.from("sunny")));
        StreamingAssistant agent = AgenticServices.agentBuilder(StreamingAssistant.class)
                .streamingChatModel(model)
                .tools(new WeatherTools())
                .toolSearchStrategy(new SimpleToolSearchStrategy())
                .build();

        assertThat(complete(agent.answer("weather")).aiMessage().text()).isEqualTo("sunny");
        assertSearch(model.requests(), "tool_search_tool");
    }

    @Test
    void should_apply_the_declarative_strategy_to_a_streaming_agent() throws Exception {
        StreamingChatModelMock model = new StreamingChatModelMock(
                List.of(searchCall("tool_search_tool"), weatherCall(), AiMessage.from("sunny")));
        DeclarativeStreamingAssistant agent = AgenticServices.agentBuilder(DeclarativeStreamingAssistant.class)
                .streamingChatModel(model)
                .build();

        assertThat(complete(agent.answer("weather")).aiMessage().text()).isEqualTo("sunny");
        assertSearch(model.requests(), "tool_search_tool");
    }

    @Test
    void should_resolve_strategy_supplier_parameters() {
        ToolSearchStrategy strategy =
                SimpleToolSearchStrategy.builder().toolName("resolved_search").build();
        SupplierParameterResolver resolver = new SupplierParameterResolver() {
            @Override
            public boolean supports(Context context) {
                return context.parameter().getType() == ToolSearchStrategy.class;
            }

            @Override
            public Object resolve(Context context) {
                return strategy;
            }
        };
        DeclarativeUtil.addSupplierParameterResolver(resolver);
        try {
            ChatModelMock model =
                    thatAlwaysResponds(searchCall("resolved_search"), weatherCall(), AiMessage.from("sunny"));
            ResolvedAssistant agent = AgenticServices.agentBuilder(ResolvedAssistant.class)
                    .chatModel(model)
                    .tools(new WeatherTools())
                    .build();

            assertThat(agent.answer("weather")).isEqualTo("sunny");
            assertSearch(model.requests(), "resolved_search");
        } finally {
            DeclarativeUtil.getSupplierParameterResolvers().remove(resolver);
        }
    }

    @Test
    void should_reject_a_supplier_with_the_wrong_return_type() throws Exception {
        String method = InvalidAssistant.class.getMethod("strategy").toString();

        assertThatThrownBy(() -> AgenticServices.agentBuilder(InvalidAssistant.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Method " + method + " must return " + ToolSearchStrategy.class.getName());
    }

    private static ChatModelMock searchModel() {
        return thatAlwaysResponds(searchCall("tool_search_tool"), weatherCall(), AiMessage.from("sunny"));
    }

    private static AiMessage searchCall(String name) {
        return AiMessage.from(ToolExecutionRequest.builder()
                .id("search")
                .name(name)
                .arguments("{\"terms\":[\"weather\"]}")
                .build());
    }

    private static AiMessage weatherCall() {
        return AiMessage.from(ToolExecutionRequest.builder()
                .id("weather")
                .name("weather")
                .arguments("{}")
                .build());
    }

    private static List<String> toolNames(ChatRequest request) {
        return request.toolSpecifications().stream()
                .map(ToolSpecification::name)
                .toList();
    }

    private static void assertSearch(List<ChatRequest> requests, String searchTool) {
        assertThat(requests).hasSize(3);
        assertThat(toolNames(requests.get(0))).containsExactlyInAnyOrder(searchTool, "ping");
        assertThat(toolNames(requests.get(1))).containsExactlyInAnyOrder(searchTool, "ping", "weather");
        assertWeatherResult(requests.get(2));
    }

    private static void assertWeatherResult(ChatRequest request) {
        assertThat(request.messages())
                .contains(ToolExecutionResultMessage.builder()
                        .id("weather")
                        .toolName("weather")
                        .text("sunny")
                        .isError(false)
                        .build());
    }

    private static ChatResponse complete(TokenStream stream) throws Exception {
        CompletableFuture<ChatResponse> result = new CompletableFuture<>();
        stream.onPartialResponse(ignored -> {})
                .onCompleteResponse(result::complete)
                .onError(result::completeExceptionally)
                .start();
        return result.get(10, SECONDS);
    }

    public interface Assistant {
        @UserMessage("{{request}}")
        @Agent(outputKey = "answer")
        String answer(@V("request") String request);
    }

    public interface MemoryAssistant {
        @UserMessage("{{request}}")
        @Agent(outputKey = "answer")
        String answer(@MemoryId String memoryId, @V("request") String request);
    }

    public interface StreamingAssistant {
        @UserMessage("{{request}}")
        @Agent(outputKey = "answer")
        TokenStream answer(@V("request") String request);
    }

    public interface DeclarativeAssistant extends Assistant {
        @ToolsSupplier
        static Object tools() {
            return new WeatherTools();
        }

        @ToolSearchStrategySupplier
        static ToolSearchStrategy strategy() {
            supplierCalls.incrementAndGet();
            return new SimpleToolSearchStrategy();
        }
    }

    public interface DeclarativeStreamingAssistant extends StreamingAssistant {
        @ToolsSupplier
        static Object tools() {
            return new WeatherTools();
        }

        @ToolSearchStrategySupplier
        static ToolSearchStrategy strategy() {
            return new SimpleToolSearchStrategy();
        }
    }

    public interface WeatherWorkflow {
        @SequenceAgent(outputKey = "answer", subAgents = DeclarativeAssistant.class)
        String answer(@V("request") String request);
    }

    public interface ConfiguredAssistant extends Assistant {
        @ToolsSupplier
        static Object tools() {
            return new WeatherTools();
        }
    }

    public interface ConfiguredWorkflow {
        @SequenceAgent(outputKey = "answer", subAgents = ConfiguredAssistant.class)
        String answer(@V("request") String request);
    }

    public interface ResolvedAssistant extends Assistant {
        @ToolSearchStrategySupplier
        static ToolSearchStrategy strategy(ToolSearchStrategy strategy) {
            return strategy;
        }
    }

    public interface InvalidAssistant extends Assistant {
        @ToolSearchStrategySupplier
        static String strategy() {
            return "invalid";
        }
    }

    public static class WeatherTools {
        @Tool("Find the weather")
        String weather() {
            return "sunny";
        }

        @Tool(searchBehavior = ALWAYS_VISIBLE)
        String ping() {
            return "pong";
        }
    }
}
