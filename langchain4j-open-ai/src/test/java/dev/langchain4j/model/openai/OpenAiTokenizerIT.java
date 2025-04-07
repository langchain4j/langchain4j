package dev.langchain4j.model.openai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.Tokenizer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.toolExecutionResultMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_3_5_TURBO_0125;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_3_5_TURBO_1106;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_0125_PREVIEW;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_1106_PREVIEW;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_32K;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_32K_0613;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_TURBO_PREVIEW;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;
import static org.junit.jupiter.params.provider.Arguments.arguments;

// TODO use exact model for Tokenizer (the one returned by LLM)
@Disabled("this test is very long and expensive, we will need to set a schedule for it to run maybe 1 time per month")
class OpenAiTokenizerIT {

    // my API key does not have access to these models
    private static final Set<OpenAiChatModelName> MODELS_WITHOUT_ACCESS = new HashSet<>(asList(
            GPT_3_5_TURBO_0125,
            GPT_4_32K,
            GPT_4_32K_0613
    ));

    private static final Set<OpenAiChatModelName> MODELS_WITH_PARALLEL_TOOL_SUPPORT = new HashSet<>(asList(
            // TODO add GPT_3_5_TURBO once it points to GPT_3_5_TURBO_1106
            GPT_3_5_TURBO_1106,
            GPT_3_5_TURBO_0125,
            GPT_4_TURBO_PREVIEW,
            GPT_4_1106_PREVIEW,
            GPT_4_0125_PREVIEW
    ));

    @ParameterizedTest
    @MethodSource
    void should_count_tokens_in_messages(List<ChatMessage> messages, OpenAiChatModelName modelName) {

        // given
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(modelName)
                .maxCompletionTokens(1) // we don't need outputs, let's not waste tokens
                .logRequests(true)
                .logResponses(true)
                .build();

        int expectedTokenCount = model.chat(messages).tokenUsage().inputTokenCount();

        Tokenizer tokenizer = new OpenAiTokenizer(modelName.toString());

        // when
        int tokenCount = tokenizer.estimateTokenCountInMessages(messages);

        // then
        assertThat(tokenCount).isEqualTo(expectedTokenCount);
    }

    static Stream<Arguments> should_count_tokens_in_messages() {
        return stream(OpenAiChatModelName.values())
                .filter(model -> !MODELS_WITHOUT_ACCESS.contains(model))
                .flatMap(model -> Stream.of(
                        arguments(singletonList(systemMessage("Be friendly.")), model),
                        arguments(singletonList(systemMessage("You are a helpful assistant, help the user!")), model),

                        arguments(singletonList(userMessage("Hi")), model),
                        arguments(singletonList(userMessage("Hello, how are you?")), model),

                        arguments(singletonList(userMessage("Stan", "Hi")), model),
                        arguments(singletonList(userMessage("Klaus", "Hi")), model),
                        arguments(singletonList(userMessage("Giovanni", "Hi")), model),

                        arguments(singletonList(aiMessage("Hi")), model),
                        arguments(singletonList(aiMessage("Hello, how can I help you?")), model),

                        arguments(asList(
                                systemMessage("Be helpful"),
                                userMessage("hi")
                        ), model),

                        arguments(asList(
                                systemMessage("Be helpful"),
                                userMessage("hi"),
                                aiMessage("Hello, how can I help you?"),
                                userMessage("tell me a joke")
                        ), model),

                        arguments(asList(
                                systemMessage("Be helpful"),
                                userMessage("hi"),
                                aiMessage("Hello, how can I help you?"),
                                userMessage("tell me a joke"),
                                aiMessage("Why don't scientists trust atoms?\n\nBecause they make up everything!"),
                                userMessage("tell me another one, this one is not funny")
                        ), model)
                ));
    }

    @ParameterizedTest
    @MethodSource
    void should_count_tokens_in_messages_with_single_tool(List<ChatMessage> messages, OpenAiChatModelName modelName) {

        // given
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(modelName)
                .maxCompletionTokens(1) // we don't need outputs, let's not waste tokens
                .logRequests(true)
                .logResponses(true)
                .build();

        int expectedTokenCount = model.chat(messages).tokenUsage().inputTokenCount();

        Tokenizer tokenizer = new OpenAiTokenizer(modelName.toString());

        // when
        int tokenCount = tokenizer.estimateTokenCountInMessages(messages);

        // then
        assertThat(tokenCount).isCloseTo(expectedTokenCount, withPercentage(4));
    }

    static Stream<Arguments> should_count_tokens_in_messages_with_single_tool() {
        return stream(OpenAiChatModelName.values())
                .filter(model -> !MODELS_WITHOUT_ACCESS.contains(model))
                .flatMap(model -> Stream.of(

                        // various tool "name" lengths
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("time") // 1 token
                                                .arguments("{}")
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "23:59")
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_time") // 2 tokens
                                                .arguments("{}")
                                                .build()
                                ),
                                toolExecutionResultMessage("b", null, "23:59")
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("c")
                                                .name("get_current_time") // 3 tokens
                                                .arguments("{}")
                                                .build()
                                ),
                                toolExecutionResultMessage("c", null, "23:59")
                        ), model),

                        // 1 argument, various argument "name" lengths
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{\"city\":\"Berlin\"}") // 1 token
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "23:59")
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_time")
                                                .arguments("{\"target_city\":\"Berlin\"}") // 2 tokens
                                                .build()
                                ),
                                toolExecutionResultMessage("b", null, "23:59")
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("c")
                                                .name("current_time")
                                                .arguments("{\"target_city_name\":\"Berlin\"}") // 3 tokens
                                                .build()
                                ),
                                toolExecutionResultMessage("c", null, "23:59")
                        ), model),

                        // 1 argument, various argument "value" lengths
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{\"city\":\"Berlin\"}") // 1 token
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "23:59")
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_time")
                                                .arguments("{\"city\":\"Munich\"}") // 3 tokens
                                                .build()
                                ),
                                toolExecutionResultMessage("b", null, "23:59")
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("c")
                                                .name("current_time")
                                                .arguments("{\"city\":\"Pietramontecorvino\"}") // 8 tokens
                                                .build()
                                ),
                                toolExecutionResultMessage("c", null, "23:59")
                        ), model),

                        // 1 argument, various numeric argument "value" lengths
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{\"city_id\": 189}") // 1 token
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "23:59")
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_time")
                                                .arguments("{\"city_id\": 189647}") // 2 tokens
                                                .build()
                                ),
                                toolExecutionResultMessage("b", null, "23:59")
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("c")
                                                .name("current_time")
                                                .arguments("{\"city_id\": 189647852}") // 3 tokens
                                                .build()
                                ),
                                toolExecutionResultMessage("c", null, "23:59")
                        ), model),

                        // 2 arguments
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{\"city\":\"Berlin\",\"country\":\"Germany\"}")
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "23:59")
                        ), model),

                        // 3 arguments
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{\"city\":\"Berlin\",\"country\":\"Germany\",\"format\":\"24\"}")
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "23:59")
                        ), model),

                        // various result lengths
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{}")
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "23") // 1 token
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_time")
                                                .arguments("{}")
                                                .build()
                                ),
                                toolExecutionResultMessage("b", null, "23:59") // 3 tokens
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("c")
                                                .name("current_time")
                                                .arguments("{}")
                                                .build()
                                ),
                                toolExecutionResultMessage("c", null, "23:59:59") // 5 tokens
                        ), model)
                ));
    }

    @ParameterizedTest
    @MethodSource
    void should_count_tokens_in_messages_with_multiple_tools(List<ChatMessage> messages,
                                                             OpenAiChatModelName modelName) {
        // given
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(modelName)
                .maxCompletionTokens(1) // we don't need outputs, let's not waste tokens
                .logRequests(true)
                .logResponses(true)
                .build();

        int expectedTokenCount = model.chat(messages).tokenUsage().inputTokenCount();

        Tokenizer tokenizer = new OpenAiTokenizer(modelName.toString());

        // when
        int tokenCount = tokenizer.estimateTokenCountInMessages(messages);

        // then
        assertThat(tokenCount).isCloseTo(expectedTokenCount, withPercentage(4));
    }

    static Stream<Arguments> should_count_tokens_in_messages_with_multiple_tools() {
        return stream(OpenAiChatModelName.values())
                .filter(model -> !MODELS_WITHOUT_ACCESS.contains(model))
                .filter(MODELS_WITH_PARALLEL_TOOL_SUPPORT::contains)
                .flatMap(model -> Stream.of(

                        // various tool "name" lengths
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("time") // 1 token
                                                .arguments("{}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("temperature") // 1 token
                                                .arguments("{}")
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "1"),
                                toolExecutionResultMessage("b", null, "2")
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time") // 2 tokens
                                                .arguments("{}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_temperature") // 2 tokens
                                                .arguments("{}")
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "1"),
                                toolExecutionResultMessage("b", null, "2")
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("get_current_time") // 3 tokens
                                                .arguments("{}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("get_current_temperature") // 3 tokens
                                                .arguments("{}")
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "1"),
                                toolExecutionResultMessage("b", null, "2")
                        ), model),

                        // 1 argument, various argument "name" lengths
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{\"city\":\"Berlin\"}") // 1 token
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_temperature")
                                                .arguments("{\"city\":\"Berlin\"}") // 1 token
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "1"),
                                toolExecutionResultMessage("b", null, "2")
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{\"target_city\":\"Berlin\"}") // 2 tokens
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_temperature")
                                                .arguments("{\"target_city\":\"Berlin\"}") // 2 tokens
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "1"),
                                toolExecutionResultMessage("b", null, "2")
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{\"target_city_name\":\"Berlin\"}") // 3 tokens
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_temperature")
                                                .arguments("{\"target_city_name\":\"Berlin\"}") // 3 tokens
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "1"),
                                toolExecutionResultMessage("b", null, "2")
                        ), model),

                        // 1 argument, various argument "value" lengths
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{\"city\":\"Berlin\"}") // 1 token
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_temperature")
                                                .arguments("{\"city\":\"Berlin\"}") // 1 token
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "1"),
                                toolExecutionResultMessage("b", null, "2")
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{\"city\":\"Munich\"}") // 3 tokens
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_temperature")
                                                .arguments("{\"city\":\"Munich\"}") // 3 tokens
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "1"),
                                toolExecutionResultMessage("b", null, "2")
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{\"city\":\"Pietramontecorvino\"}") // 8 tokens
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_temperature")
                                                .arguments("{\"city\":\"Pietramontecorvino\"}") // 8 tokens
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "1"),
                                toolExecutionResultMessage("b", null, "2")
                        ), model),

                        // 1 argument, various numeric argument "value" lengths
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{\"city_id\": 189}") // 1 token
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_temperature")
                                                .arguments("{\"city_id\": 189}") // 1 token
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "1"),
                                toolExecutionResultMessage("b", null, "2")
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{\"city_id\": 189647}") // 2 tokens
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_temperature")
                                                .arguments("{\"city_id\": 189647}") // 2 tokens
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "1"),
                                toolExecutionResultMessage("b", null, "2")
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{\"city_id\": 189647852}") // 3 tokens
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_temperature")
                                                .arguments("{\"city_id\": 189647852}") // 3 tokens
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "1"),
                                toolExecutionResultMessage("b", null, "2")
                        ), model),

                        // 2 arguments
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{\"city\":\"Berlin\",\"country\":\"Germany\"}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_temperature")
                                                .arguments("{\"city\":\"Berlin\",\"country\":\"Germany\"}")
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "1"),
                                toolExecutionResultMessage("b", null, "2")
                        ), model),

                        // 3 arguments
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{\"city\":\"Berlin\",\"country\":\"Germany\",\"format\":\"24\"}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_temperature")
                                                .arguments("{\"city\":\"Berlin\",\"country\":\"Germany\",\"unit\":\"C\"}")
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "1"),
                                toolExecutionResultMessage("b", null, "2")
                        ), model),

                        // various result lengths
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_temperature")
                                                .arguments("{}")
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "23"), // 1 token
                                toolExecutionResultMessage("b", null, "17") // 1 token
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_temperature")
                                                .arguments("{}")
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "23:59"), // 3 tokens
                                toolExecutionResultMessage("b", null, "17.5") // 3 tokens
                        ), model),
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("current_time")
                                                .arguments("{}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("current_temperature")
                                                .arguments("{}")
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "23:59:59"), // 5 tokens
                                toolExecutionResultMessage("b", null, "17.5 grad C") // 5 tokens
                        ), model),

                        // 3 tools without arguments
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("time")
                                                .arguments("{}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("temperature")
                                                .arguments("{}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("c")
                                                .name("weather")
                                                .arguments("{}")
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "1"),
                                toolExecutionResultMessage("b", null, "2"),
                                toolExecutionResultMessage("c", null, "3")
                        ), model),

                        // 3 tools with arguments
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("time")
                                                .arguments("{\"city\":\"Berlin\"}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("temperature")
                                                .arguments("{\"city\":\"Berlin\"}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("c")
                                                .name("weather")
                                                .arguments("{\"city\":\"Berlin\"}")
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "1"),
                                toolExecutionResultMessage("b", null, "2"),
                                toolExecutionResultMessage("c", null, "3")
                        ), model),

                        // 4 tools without arguments
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("time")
                                                .arguments("{}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("temperature")
                                                .arguments("{}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("c")
                                                .name("weather")
                                                .arguments("{}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("d")
                                                .name("UV")
                                                .arguments("{}")
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "1"),
                                toolExecutionResultMessage("b", null, "2"),
                                toolExecutionResultMessage("c", null, "3"),
                                toolExecutionResultMessage("d", null, "4")
                        ), model),

                        // 4 tools with arguments
                        arguments(asList(
                                aiMessage(
                                        ToolExecutionRequest.builder()
                                                .id("a")
                                                .name("time")
                                                .arguments("{\"city\":\"Berlin\"}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("b")
                                                .name("temperature")
                                                .arguments("{\"city\":\"Berlin\"}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("c")
                                                .name("weather")
                                                .arguments("{\"city\":\"Berlin\"}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("d")
                                                .name("UV")
                                                .arguments("{\"city\":\"Berlin\"}")
                                                .build()
                                ),
                                toolExecutionResultMessage("a", null, "1"),
                                toolExecutionResultMessage("b", null, "2"),
                                toolExecutionResultMessage("c", null, "3"),
                                toolExecutionResultMessage("d", null, "4")
                        ), model)
                ));
    }
}
