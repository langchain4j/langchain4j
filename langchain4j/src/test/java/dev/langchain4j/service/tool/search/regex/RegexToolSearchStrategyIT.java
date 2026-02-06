package dev.langchain4j.service.tool.search.regex;

import dev.langchain4j.LoggingChatModelListener;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

import java.util.List;

import static dev.langchain4j.MockitoUtils.ignoreInteractions;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static dev.langchain4j.service.common.AbstractAiServiceWithToolsIT.containsTool;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class RegexToolSearchStrategyIT {

    // TODO unit test error cases

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

        @SystemMessage("Use 'tool_search_tool_regex' tool if you need to discover other available tools")
        String chat(String userMessage);
    }

    class Tools {

        @Tool
        String getWeather(@P("city") String city) {
            return "sunny";
        }

        @Tool
        String getTime(@P("country") String country) {
            return "12:34:56";
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_tool_search_tool(ChatModel model) {

        // given
        ChatModel spyModel = spy(model);
        Tools spyTools = spy(new Tools());
        ToolSearchStrategy spyToolSearchStrategy = spy(new RegexToolSearchStrategy());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyModel)
                .tools(spyTools)
                .toolSearchStrategy(spyToolSearchStrategy)
                .build();

        // when
        String answer = assistant.chat("What is the weather in London?");

        // then
        assertThat(answer.toLowerCase()).contains("sun");

        InOrder inOrder = inOrder(spyModel, spyToolSearchStrategy, spyTools);

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 1
                        && containsTool(request, "tool_search_tool_regex")
        ));

        inOrder.verify(spyToolSearchStrategy).search(argThat(request ->
                request.toolSearchRequest().arguments().contains("regex")
        ));

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool_regex")
                        && containsTool(request, "getWeather")
        ));

        inOrder.verify(spyTools).getWeather("London");

        inOrder.verify(spyModel).chat(argThat((ChatRequest request) ->
                request.toolSpecifications().size() == 2
                        && containsTool(request, "tool_search_tool_regex")
                        && containsTool(request, "getWeather")
        ));

        verifyNoMoreInteractionsFor(spyModel);
        ignoreInteractions(spyToolSearchStrategy).toolSearchTools(any());
        verifyNoMoreInteractions(spyToolSearchStrategy);
        verifyNoMoreInteractions(spyTools);
    }
}