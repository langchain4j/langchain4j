package dev.langchain4j.service.tool.search.vector;

import dev.langchain4j.LoggingChatModelListener;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SpyingHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

import java.util.List;

import static dev.langchain4j.MockitoUtils.ignoreInteractions;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static dev.langchain4j.service.AiServicesWithToolSearchToolIT.containsTool;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class VectorToolSearchStrategyIT {

    private static OpenAiChatModel.OpenAiChatModelBuilder baseModelBuilder() {
        return OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .temperature(0.0)
                .listeners(new LoggingChatModelListener());
    }

    private static List<ChatModel> models() {
        return List.of(
                baseModelBuilder().build(),
                baseModelBuilder().strictTools(true).build()
        );
    }

    interface Assistant {

        @SystemMessage("Use 'tool_search_tool' tool if you need to discover other available tools")
        String chat(String userMessage);
    }

    class Tools {

        static final String GET_WETHER_DESCRIPTION = "Returns the weather for a given city";
        static final String GET_TIME_DESCRIPTION = "Returns the time for a given country";

        @Tool(GET_WETHER_DESCRIPTION)
        String getWeather(@P("city") String city) {
            return "sunny";
        }

        @Tool(GET_TIME_DESCRIPTION)
        String getTime(@P("country") String country) {
            return "12:34:56";
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_tool_search_tool(ChatModel chatModel) {

        // given
        SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("text-embedding-3-large")
                .build();

        ChatModel spyChatModel = spy(chatModel);
        Tools spyTools = spy(new Tools());
        VectorToolSearchStrategy spyToolSearchStrategy = spy(VectorToolSearchStrategy.builder()
                .embeddingModel(embeddingModel)
                .maxResults(1)
                .build());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .tools(spyTools)
                .toolSearchStrategy(spyToolSearchStrategy)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
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
                request.toolExecutionRequest().arguments().contains("weather")
        ));

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ));

        inOrder.verify(spyTools).getWeather("London");

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
        ignoreInteractions(spyToolSearchStrategy).getToolSearchTools(any());
        ignoreInteractions(spyToolSearchStrategy).format(any());
        verifyNoMoreInteractions(spyToolSearchStrategy);
        verifyNoMoreInteractions(spyTools);

        assertThat(spyingHttpClient.requests()).hasSize(1);
        assertThat(spyingHttpClient.requests().get(0).body()).contains("getWeather: " + Tools.GET_WETHER_DESCRIPTION);
        assertThat(spyingHttpClient.requests().get(0).body()).contains("getTime: " + Tools.GET_TIME_DESCRIPTION);

        // when
        String answer2 = assistant.chat("What is the time in Germany?");

        // then
        assertThat(answer2).contains("12", "34");

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
        ));

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                request.toolExecutionRequest().arguments().contains("time")
        ));

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ));

        inOrder.verify(spyTools).getTime("Germany");

        inOrder.verify(spyChatModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 3
                        && containsTool(request, "tool_search_tool")
                        && containsTool(request, "getWeather")
                        && containsTool(request, "getTime")
        ));

        verifyNoMoreInteractionsFor(spyChatModel);
        ignoreInteractions(spyToolSearchStrategy).getToolSearchTools(any());
        ignoreInteractions(spyToolSearchStrategy).format(any());
        verifyNoMoreInteractions(spyToolSearchStrategy);
        verifyNoMoreInteractions(spyTools);

        // testing caching
        assertThat(spyingHttpClient.requests()).hasSize(2);
        assertThat(spyingHttpClient.requests().get(1).body()).doesNotContain("getWeather");
        assertThat(spyingHttpClient.requests().get(1).body()).doesNotContain("getTime");
    }
}