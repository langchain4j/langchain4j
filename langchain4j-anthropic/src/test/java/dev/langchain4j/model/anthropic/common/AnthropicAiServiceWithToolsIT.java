package dev.langchain4j.model.anthropic.common;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_SONNET_4_5_20250929;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SpyingHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicAiServiceWithToolsIT extends AbstractAiServiceWithToolsIT {

    @Override
    protected List<ChatModel> models() {
        return singletonList(AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build());
    }

    @Test
    void should_support_tool_search_tool() { // TODO document

        // given
        Map<String, Object> toolSearchTool = Map.of(
                "type", "tool_search_tool_regex_20251119",
                "name", "tool_search_tool_regex"
        );

        class Tools {

            @Tool
            String getWeather(String location) {
                return "sunny";
            }

            @Tool
            String getTime(String location) {
                return "12:34:56";
            }
        }

        SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());

        String deferLoadingKey = "defer_loading";

        ChatModel chatModel = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_SONNET_4_5_20250929)
                .beta("advanced-tool-use-2025-11-20")
                .serverTools(toolSearchTool)
                .sendToolMetadataKeys(deferLoadingKey)
                .logRequests(true)
                .logResponses(true)
                .build();

        interface Assistant {

            @SystemMessage("Use tool search if needed")
            String chat(String userMessage);
        }

        Tools tools = spy(new Tools());

        UnaryOperator<ChatRequest> chatRequestTransformer = chatRequest -> {

            List<ToolSpecification> updatedToolSpecifications = chatRequest.toolSpecifications().stream()
                    .map(toolSpecification -> {
                        if (toolSpecification.name().equals("getWeather")) {
                            return toolSpecification.toBuilder()
                                    .addMetadata(deferLoadingKey, true)
                                    .build();
                        } else {
                            return toolSpecification;
                        }
                    })
                    .toList();

            ChatRequestParameters updatedParameters = chatRequest.parameters() // TODO simplify
                    .overrideWith(ChatRequestParameters.builder()
                            .toolSpecifications(updatedToolSpecifications)
                            .build());

            return chatRequest.toBuilder()
                    .parameters(updatedParameters)
                    .build();
        };

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(tools)
                .chatRequestTransformer(chatRequestTransformer)
                .build();

        // when
        assistant.chat("What is the weather in Munich?");

        // then
        assertThat(spyingHttpClient.requests().get(0).body())
                .contains(toolSearchTool.get("type").toString())
                .contains(deferLoadingKey);

        verify(tools).getWeather("Munich");
        verifyNoMoreInteractions(tools);
    }
}
