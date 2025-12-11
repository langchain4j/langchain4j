package dev.langchain4j.model.anthropic.common;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SpyingHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_SONNET_4_5_20250929;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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

            @Tool(metadata = "{\"defer_loading\": true}") // TODO document
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
                .toolMetadataKeysToSend(deferLoadingKey)
                .logRequests(true)
                .logResponses(true)
                .build();

        interface Assistant {

            @SystemMessage("Use tool search if needed")
            String chat(String userMessage);
        }

        Tools tools = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(tools)
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

    @Test
    void should_support_programmatic_tool_calling() { // TODO document

        // given
        Map<String, Object> codeExecutionTool = Map.of(
                "type", "code_execution_20250825",
                "name", "code_execution"
        );

        class Tools {

            static final String DESCRIPTION = """
                    Returns daily minimum and maximum temperatures recorded
                    for a specified city for a specified number of previous days.
                    Response format: [{"min":0.0,"max":10.0},{"min":0.0,"max":20.0},{"min":0.0,"max":30.0}]
                    """;
            static final String METADATA = "{\"allowed_callers\": [\"code_execution_20250825\"]}";

            record TemperatureRange(double min, double max) {}

            @Tool(value = DESCRIPTION, metadata = METADATA)
            List<TemperatureRange> getMaxDailyTemperatures(String city, int days) {
                if ("Munich".equals(city) && days == 5) {
                    return List.of(
                            new TemperatureRange(0.0, 1.0),
                            new TemperatureRange(0.0, 2.0),
                            new TemperatureRange(0.0, 3.0),
                            new TemperatureRange(0.0, 4.0),
                            new TemperatureRange(0.0, 5.0)
                    );
                }

                throw new IllegalArgumentException("Unknown city: " + city + " or days: " + days);
            }

            @Tool(value = "Calculates the average of the specified list of numbers", metadata = METADATA)
            Double average(List<Double> numbers) {
                return numbers.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElseThrow();
            }
        }

        SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());

        ChatModel chatModel = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_SONNET_4_5_20250929)
                .beta("advanced-tool-use-2025-11-20")
                .serverTools(codeExecutionTool)
                .toolMetadataKeysToSend("allowed_callers")
                .logRequests(true)
                .logResponses(true)
                .build();

        interface Assistant {

            String chat(String userMessage);
        }

        Tools tools = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(tools)
                .build();

        // when
        assistant.chat("What was the average max temperature in Munich in the last 5 days?");

        // then
        assertThat(spyingHttpClient.requests().get(0).body())
                .contains(codeExecutionTool.get("type").toString())
                .contains("allowed_callers");

        verify(tools).getMaxDailyTemperatures("Munich", 5);
        verify(tools).average(List.of(1.0, 2.0, 3.0, 4.0, 5.0));
        verifyNoMoreInteractions(tools);
    }

    @Test
    void should_support_tool_examples() { // TODO document

        // given
        enum Unit {
            CELSIUS, FAHRENHEIT
        }

        class Tools {

            public static final String TOOL_METADATA = """
                    {
                        "input_examples": [
                            {
                                "arg0": "San Francisco, CA",
                                "arg1": "FAHRENHEIT"
                            },
                            {
                                "arg0": "Tokyo, Japan",
                                "arg1": "CELSIUS"
                            },
                            {
                                "arg0": "New York, NY"
                            }
                        ]
                    }
                    """;

            @Tool(metadata = TOOL_METADATA)
            String getWeather(String location, @P(value = "temperature unit", required = false) Unit unit) {
                return "sunny";
            }
        }

        SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());

        ChatModel chatModel = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_SONNET_4_5_20250929)
                .beta("advanced-tool-use-2025-11-20")
                .toolMetadataKeysToSend("input_examples")
                .logRequests(true)
                .logResponses(true)
                .build();

        interface Assistant {

            String chat(String userMessage);
        }

        Tools tools = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(tools)
                .build();

        // when
        assistant.chat("What is the weather in Munich in Fahrenheit?");

        // then
        assertThat(spyingHttpClient.requests().get(0).body()).contains("input_examples");

        verify(tools).getWeather(argThat(location -> location.contains("Munich")), eq(Unit.FAHRENHEIT));
        verifyNoMoreInteractions(tools);
    }
}
