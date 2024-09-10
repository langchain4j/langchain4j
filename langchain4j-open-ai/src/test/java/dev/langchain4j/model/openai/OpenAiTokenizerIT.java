package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.chat.ChatCompletionModel;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentest4j.AssertionFailedError;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static dev.ai4j.openai4j.chat.ChatCompletionModel.*;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.*;
import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.toolExecutionResultMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
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
    private static final Set<ChatCompletionModel> MODELS_WITHOUT_ACCESS = new HashSet<>(asList(
            GPT_3_5_TURBO_0125,
            GPT_4_32K,
            GPT_4_32K_0314,
            GPT_4_32K_0613
    ));

    private static final Set<ChatCompletionModel> MODELS_WITH_PARALLEL_TOOL_SUPPORT = new HashSet<>(asList(
            // TODO add GPT_3_5_TURBO once it points to GPT_3_5_TURBO_1106
            GPT_3_5_TURBO_1106,
            GPT_3_5_TURBO_0125,
            GPT_4_TURBO_PREVIEW,
            GPT_4_1106_PREVIEW,
            GPT_4_0125_PREVIEW
    ));

    @ParameterizedTest
    @MethodSource
    void should_count_tokens_in_messages(List<ChatMessage> messages, ChatCompletionModel modelName) {

        // given
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(modelName.toString())
                .maxTokens(1) // we don't need outputs, let's not waste tokens
                .logRequests(true)
                .logResponses(true)
                .build();

        int expectedTokenCount = model.generate(messages).tokenUsage().inputTokenCount();

        Tokenizer tokenizer = new OpenAiTokenizer(modelName.toString());

        // when
        int tokenCount = tokenizer.estimateTokenCountInMessages(messages);

        // then
        assertThat(tokenCount).isEqualTo(expectedTokenCount);
    }

    static Stream<Arguments> should_count_tokens_in_messages() {
        return stream(ChatCompletionModel.values())
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
    void should_count_tokens_in_messages_with_single_tool(List<ChatMessage> messages, ChatCompletionModel modelName) {

        // given
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(modelName.toString())
                .maxTokens(1) // we don't need outputs, let's not waste tokens
                .logRequests(true)
                .logResponses(true)
                .build();

        int expectedTokenCount = model.generate(messages).tokenUsage().inputTokenCount();

        Tokenizer tokenizer = new OpenAiTokenizer(modelName.toString());

        // when
        int tokenCount = tokenizer.estimateTokenCountInMessages(messages);

        // then
        assertThat(tokenCount).isCloseTo(expectedTokenCount, withPercentage(4));
    }

    static Stream<Arguments> should_count_tokens_in_messages_with_single_tool() {
        return stream(ChatCompletionModel.values())
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
                                                             ChatCompletionModel modelName) {
        // given
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(modelName.toString())
                .maxTokens(1) // we don't need outputs, let's not waste tokens
                .logRequests(true)
                .logResponses(true)
                .build();

        int expectedTokenCount = model.generate(messages).tokenUsage().inputTokenCount();

        Tokenizer tokenizer = new OpenAiTokenizer(modelName.toString());

        // when
        int tokenCount = tokenizer.estimateTokenCountInMessages(messages);

        // then
        assertThat(tokenCount).isCloseTo(expectedTokenCount, withPercentage(4));
    }

    static Stream<Arguments> should_count_tokens_in_messages_with_multiple_tools() {
        return stream(ChatCompletionModel.values())
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

    @ParameterizedTest
    @MethodSource
    void should_count_tokens_in_tool_specifications(List<ToolSpecification> toolSpecifications,
                                                    ChatCompletionModel modelName) {
        // given
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(modelName.toString())
                .maxTokens(2) // we don't need outputs, let's not waste tokens
                .logRequests(true)
                .logResponses(true)
                .build();

        List<ChatMessage> dummyMessages = singletonList(userMessage("hi"));

        Tokenizer tokenizer = new OpenAiTokenizer(modelName.toString());

        int expectedTokenCount = model.generate(dummyMessages, toolSpecifications).tokenUsage().inputTokenCount()
                - tokenizer.estimateTokenCountInMessages(dummyMessages);

        // when
        int tokenCount = tokenizer.estimateTokenCountInToolSpecifications(toolSpecifications);

        // then
        assertThat(tokenCount).isCloseTo(expectedTokenCount, withPercentage(2));
    }

    static Stream<Arguments> should_count_tokens_in_tool_specifications() {
        return stream(ChatCompletionModel.values())
                .filter(model -> !MODELS_WITHOUT_ACCESS.contains(model))
                .flatMap(model -> Stream.of(

                        // "name" of various lengths
                        arguments(singletonList(ToolSpecification.builder()
                                .name("time") // 1 token
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time") // 2 tokens
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("get_current_time") // 3 tokens
                                .build()), model),

                        // "description" of various lengths
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("time") // 1 token
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time") // 2 tokens
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("returns current time in 24-hour format") // 8 tokens
                                .build()), model),

                        // 1 parameter with "name" of various lengths
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addParameter("city") // 1 token
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addParameter("target_city") // 2 tokens
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addParameter("target_city_name") // 3 tokens
                                .build()), model),

                        // 1 parameter with "description" of various lengths
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addParameter("city", description("city")) // 1 token
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addParameter("city", description("target city name")) // 3 tokens
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addParameter("city", description("city for which time should be returned")) // 7 tokens
                                .build()), model),

                        // 1 parameter with varying "type"
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addParameter("city", STRING)
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addParameter("city", INTEGER)
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addParameter("cities", ARRAY, items(INTEGER))
                                .build()), model),

                        // 1 parameter with "enum" of various range of values
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_temperature")
                                .description("current temperature")
                                .addParameter("unit", enums("C"))
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_temperature")
                                .description("current temperature")
                                .addParameter("unit", enums("C", "K"))
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_temperature")
                                .description("current temperature")
                                .addParameter("unit", enums("C", "K", "F"))
                                .build()), model),

                        // 1 parameter with "enum" of various name lengths
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_temperature")
                                .description("current temperature")
                                .addParameter("unit", enums("celsius", "kelvin", "fahrenheit")) // 2 tokens each
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_temperature")
                                .description("current temperature")
                                .addParameter("unit", enums("CELSIUS", "KELVIN", "FAHRENHEIT")) // 3-5 tokens
                                .build()), model),

                        // 2 parameters with "name" of various length
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addParameter("city") // 1 token
                                .addParameter("country") // 1 token
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addParameter("target_city") // 2 tokens
                                .addParameter("target_country") // 2 tokens
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addParameter("target_city_name") // 3 tokens
                                .addParameter("target_country_name") // 3 tokens
                                .build()), model),

                        // 3 parameters with "name" of various length
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addParameter("city") // 1 token
                                .addParameter("country") // 1 token
                                .addParameter("format", enums("12H", "24H")) // 1 token
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addParameter("target_city") // 2 tokens
                                .addParameter("target_country") // 2 tokens
                                .addParameter("time_format", enums("12H", "24H")) // 2 tokens
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addParameter("target_city_name") // 3 tokens
                                .addParameter("target_country_name") // 3 tokens
                                .addParameter("current_time_format", enums("12H", "24H")) // 3 tokens
                                .build()), model),

                        // 1 optional parameter with "name" of various lengths
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addOptionalParameter("city") // 1 token
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addOptionalParameter("target_city") // 2 tokens
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addOptionalParameter("target_city_name") // 3 tokens
                                .build()), model),

                        // 2 optional parameters with "name" of various lengths
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addOptionalParameter("city") // 1 token
                                .addOptionalParameter("country") // 1 token
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addOptionalParameter("target_city") // 2 tokens
                                .addOptionalParameter("target_country") // 2 tokens
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addOptionalParameter("target_city_name") // 3 tokens
                                .addOptionalParameter("target_country_name") // 3 tokens
                                .build()), model),

                        // 1 mandatory, 1 optional parameters with "name" of various lengths
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addParameter("city") // 1 token
                                .addOptionalParameter("country") // 1 token
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addParameter("target_city") // 2 tokens
                                .addOptionalParameter("target_country") // 2 tokens
                                .build()), model),
                        arguments(singletonList(ToolSpecification.builder()
                                .name("current_time")
                                .description("current time")
                                .addParameter("target_city_name") // 3 tokens
                                .addOptionalParameter("target_country_name") // 3 tokens
                                .build()), model),

                        // 2 tools
                        arguments(asList(
                                ToolSpecification.builder()
                                        .name("current_time")
                                        .description("current time")
                                        .addParameter("city_name", description("city name"))
                                        .addOptionalParameter("country_name", description("optional country name"))
                                        .build(),
                                ToolSpecification.builder()
                                        .name("current_temperature")
                                        .description("current temperature")
                                        .addParameter("city_name", description("city name"))
                                        .addOptionalParameter("country_name", description("optional country name"))
                                        .build()
                        ), model),

                        // 3 tools
                        arguments(asList(
                                ToolSpecification.builder()
                                        .name("current_time")
                                        .description("current time")
                                        .addParameter("city_name", description("city name"))
                                        .addOptionalParameter("country_name", description("optional country name"))
                                        .build(),
                                ToolSpecification.builder()
                                        .name("current_temperature")
                                        .description("current temperature")
                                        .addParameter("city_name", description("city name"))
                                        .addOptionalParameter("country_name", description("optional country name"))
                                        .build(),
                                ToolSpecification.builder()
                                        .name("current_weather")
                                        .description("current weather")
                                        .addParameter("city_name", description("city name"))
                                        .addOptionalParameter("country_name", description("optional country name"))
                                        .build()
                        ), model)
                ));
    }

    @ParameterizedTest
    @MethodSource
    void should_count_tokens_in_tool_execution_request(UserMessage userMessage,
                                                       ToolSpecification toolSpecification,
                                                       ToolExecutionRequest expectedToolExecutionRequest,
                                                       ChatCompletionModel modelName) {
        // given
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(modelName.toString())
                .logRequests(true)
                .logResponses(true)
                .build();

        Response<AiMessage> response = model.generate(singletonList(userMessage), singletonList(toolSpecification));

        List<ToolExecutionRequest> toolExecutionRequests = response.content().toolExecutionRequests();
        // we need to ensure that model generated expected tool execution request,
        // then we can use output token count as a reference
        assertThat(toolExecutionRequests).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = toolExecutionRequests.get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(expectedToolExecutionRequest.name());
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace(expectedToolExecutionRequest.arguments());

        int expectedTokenCount = response.tokenUsage().outputTokenCount();

        Tokenizer tokenizer = new OpenAiTokenizer(modelName.toString());

        // when
        int tokenCount = tokenizer.estimateTokenCountInToolExecutionRequests(toolExecutionRequests);

        // then
        try {
            assertThat(tokenCount).isEqualTo(expectedTokenCount);
        } catch (AssertionFailedError e) {
            if (modelName == GPT_3_5_TURBO_1106) {
                // sometimes GPT_3_5_TURBO_1106 calculates tokens wrongly
                // see https://community.openai.com/t/inconsistent-token-billing-for-tool-calls-in-gpt-3-5-turbo-1106
                // TODO remove once they fix it
                e.printStackTrace();
                // there is some pattern to it, so we are going to check if this is really the case or our calculation is wrong
                Tokenizer tokenizer2 = new OpenAiTokenizer(GPT_3_5_TURBO.toString());
                int tokenCount2 = tokenizer2.estimateTokenCountInToolExecutionRequests(toolExecutionRequests);
                assertThat(tokenCount2).isEqualTo(expectedTokenCount - 3);
            } else {
                throw e;
            }
        }
    }

    @ParameterizedTest
    @MethodSource("should_count_tokens_in_tool_execution_request")
    void should_count_tokens_in_forceful_tool_specification_and_execution_request(UserMessage userMessage,
                                                                                  ToolSpecification toolSpecification,
                                                                                  ToolExecutionRequest expectedToolExecutionRequest,
                                                                                  ChatCompletionModel modelName) {
        // given
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(modelName.toString())
                .logRequests(true)
                .logResponses(true)
                .build();

        Response<AiMessage> response = model.generate(singletonList(userMessage), toolSpecification);

        Tokenizer tokenizer = new OpenAiTokenizer(modelName.toString());

        int expectedTokenCountInSpecification = response.tokenUsage().inputTokenCount()
                - tokenizer.estimateTokenCountInMessages(singletonList(userMessage));

        // when
        int tokenCountInSpecification = tokenizer.estimateTokenCountInForcefulToolSpecification(toolSpecification);

        // then
        assertThat(tokenCountInSpecification).isEqualTo(expectedTokenCountInSpecification);

        // given
        List<ToolExecutionRequest> toolExecutionRequests = response.content().toolExecutionRequests();
        // we need to ensure that model generated expected tool execution request,
        // then we can use output token count as a reference
        assertThat(toolExecutionRequests).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = toolExecutionRequests.get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(expectedToolExecutionRequest.name());
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace(expectedToolExecutionRequest.arguments());

        int expectedTokenCountInToolRequest = response.tokenUsage().outputTokenCount();

        // when
        int tokenCountInToolRequest = tokenizer.estimateTokenCountInForcefulToolExecutionRequest(toolExecutionRequest);

        // then
        assertThat(tokenCountInToolRequest).isEqualTo(expectedTokenCountInToolRequest);
    }

    static Stream<Arguments> should_count_tokens_in_tool_execution_request() {
        return stream(ChatCompletionModel.values())
                .filter(model -> !MODELS_WITHOUT_ACCESS.contains(model))
                .flatMap(model -> Stream.of(

                        // no arguments, different lengths of "name"
                        arguments(
                                userMessage("What is the time now?"),
                                ToolSpecification.builder()
                                        .name("time") // 1 token
                                        .description("returns current time")
                                        .build(),
                                ToolExecutionRequest.builder()
                                        .name("time") // 1 token
                                        .arguments("{}")
                                        .build(),
                                model
                        ),
                        arguments(
                                userMessage("What is the time now?"),
                                ToolSpecification.builder()
                                        .name("current_time") // 2 tokens
                                        .description("returns current time")
                                        .build(),
                                ToolExecutionRequest.builder()
                                        .name("current_time") // 2 tokens
                                        .arguments("{}")
                                        .build(),
                                model
                        ),
                        arguments(
                                userMessage("What is the time now?"),
                                ToolSpecification.builder()
                                        .name("get_current_time") // 3 tokens
                                        .description("returns current time")
                                        .build(),
                                ToolExecutionRequest.builder()
                                        .name("get_current_time") // 3 tokens
                                        .arguments("{}")
                                        .build(),
                                model
                        ),

                        // one argument, different lengths of "arguments"
                        arguments(
                                userMessage("What is the time in Berlin now?"),
                                ToolSpecification.builder()
                                        .name("current_time")
                                        .description("returns current time")
                                        .addParameter("city")
                                        .build(),
                                ToolExecutionRequest.builder()
                                        .name("current_time")
                                        .arguments("{\"city\":\"Berlin\"}") // 5 tokens
                                        .build(),
                                model
                        ),
                        arguments(
                                userMessage("What is the time in Munich now?"),
                                ToolSpecification.builder()
                                        .name("current_time")
                                        .description("returns current time")
                                        .addParameter("city")
                                        .build(),
                                ToolExecutionRequest.builder()
                                        .name("current_time")
                                        .arguments("{\"city\":\"Munich\"}") // 7 tokens
                                        .build(),
                                model
                        ),
                        arguments(
                                userMessage("What is the time in Pietramontecorvino now?"),
                                ToolSpecification.builder()
                                        .name("current_time")
                                        .description("returns current time")
                                        .addParameter("city")
                                        .build(),
                                ToolExecutionRequest.builder()
                                        .name("current_time")
                                        .arguments("{\"city\":\"Pietramontecorvino\"}") // 12 tokens
                                        .build(),
                                model
                        ),

                        // two arguments, different lengths of "arguments"
                        arguments(
                                userMessage("What is the time in Berlin now?"),
                                ToolSpecification.builder()
                                        .name("current_time")
                                        .description("returns current time")
                                        .addParameter("city")
                                        .addParameter("country")
                                        .build(),
                                ToolExecutionRequest.builder()
                                        .name("current_time")
                                        .arguments("{\"country\":\"Germany\",\"city\":\"Berlin\"}") // 9 tokens
                                        .build(),
                                model
                        ),
                        arguments(
                                userMessage("What is the time in Munich now?"),
                                ToolSpecification.builder()
                                        .name("current_time")
                                        .description("returns current time")
                                        .addParameter("city")
                                        .addParameter("country")
                                        .build(),
                                ToolExecutionRequest.builder()
                                        .name("current_time")
                                        .arguments("{\"country\":\"Germany\",\"city\":\"Munich\"}") // 11 tokens
                                        .build(),
                                model
                        ),
                        arguments(
                                userMessage("What is the time in Pietramontecorvino now?"),
                                ToolSpecification.builder()
                                        .name("current_time")
                                        .description("returns current time")
                                        .addParameter("city")
                                        .addParameter("country")
                                        .build(),
                                ToolExecutionRequest.builder()
                                        .name("current_time")
                                        .arguments("{\"country\":\"Italy\",\"city\":\"Pietramontecorvino\"}") // 16 tokens
                                        .build(),
                                model
                        ),

                        // three arguments, different lengths of "arguments"
                        arguments(
                                userMessage("What is the time in Berlin now in 24-hour format?"),
                                ToolSpecification.builder()
                                        .name("current_time")
                                        .description("returns current time")
                                        .addParameter("city")
                                        .addParameter("country")
                                        .addParameter("format", enums("12", "24"))
                                        .build(),
                                ToolExecutionRequest.builder()
                                        .name("current_time")
                                        .arguments("{\"country\":\"Germany\",\"city\":\"Berlin\",\"format\":\"24\"}") // 13 tokens
                                        .build(),
                                model
                        ),
                        arguments(
                                userMessage("What is the time in Munich now in 24-hour format?"),
                                ToolSpecification.builder()
                                        .name("current_time")
                                        .description("returns current time")
                                        .addParameter("city")
                                        .addParameter("country")
                                        .addParameter("format", enums("12", "24"))
                                        .build(),
                                ToolExecutionRequest.builder()
                                        .name("current_time")
                                        .arguments("{\"country\":\"Germany\",\"city\":\"Munich\",\"format\":\"24\"}") // 15 tokens
                                        .build(),
                                model
                        ),
                        arguments(
                                userMessage("What is the time in Pietramontecorvino now in 24-hour format?"),
                                ToolSpecification.builder()
                                        .name("current_time")
                                        .description("returns current time")
                                        .addParameter("city")
                                        .addParameter("country")
                                        .addParameter("format", enums("12", "24"))
                                        .build(),
                                ToolExecutionRequest.builder()
                                        .name("current_time")
                                        .arguments("{\"country\":\"Italy\",\"city\":\"Pietramontecorvino\",\"format\":\"24\"}") // 20 tokens
                                        .build(),
                                model
                        )
                ));
    }

    @ParameterizedTest
    @MethodSource
    void should_count_tokens_in_multiple_tool_execution_requests(UserMessage userMessage,
                                                                 List<ToolSpecification> toolSpecifications,
                                                                 List<ToolExecutionRequest> expectedToolExecutionRequests,
                                                                 ChatCompletionModel modelName) {
        // given
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(modelName.toString())
                .logRequests(true)
                .logResponses(true)
                .build();

        Response<AiMessage> response = model.generate(singletonList(userMessage), toolSpecifications);

        List<ToolExecutionRequest> toolExecutionRequests = response.content().toolExecutionRequests();
        // we need to ensure that model generated expected tool execution requests,
        // then we can use output token count as a reference
        assertThat(toolExecutionRequests).hasSize(expectedToolExecutionRequests.size());
        for (int i = 0; i < toolExecutionRequests.size(); i++) {
            ToolExecutionRequest toolExecutionRequest = toolExecutionRequests.get(i);
            ToolExecutionRequest expectedToolExecutionRequest = expectedToolExecutionRequests.get(i);
            assertThat(toolExecutionRequest.name()).isEqualTo(expectedToolExecutionRequest.name());
            assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace(expectedToolExecutionRequest.arguments());
        }

        int expectedTokenCount = response.tokenUsage().outputTokenCount();

        Tokenizer tokenizer = new OpenAiTokenizer(modelName.toString());

        // when
        int tokenCount = tokenizer.estimateTokenCountInToolExecutionRequests(toolExecutionRequests);

        // then
        assertThat(tokenCount).isEqualTo(expectedTokenCount);
    }

    static Stream<Arguments> should_count_tokens_in_multiple_tool_execution_requests() {
        return stream(ChatCompletionModel.values())
                .filter(model -> !MODELS_WITHOUT_ACCESS.contains(model))
                .filter(MODELS_WITH_PARALLEL_TOOL_SUPPORT::contains)
                .flatMap(model -> Stream.of(

                        // no arguments, different lengths of "name"
                        arguments(
                                userMessage("What is the time and date now?"),
                                asList(
                                        ToolSpecification.builder()
                                                .name("time")
                                                .description("returns current time")
                                                .build(),
                                        ToolSpecification.builder()
                                                .name("date")
                                                .description("returns current date")
                                                .build()
                                ),
                                asList(
                                        ToolExecutionRequest.builder()
                                                .name("time") // 1 token
                                                .arguments("{}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .name("date") // 1 token
                                                .arguments("{}")
                                                .build()
                                ),
                                model
                        ),
                        arguments(
                                userMessage("What is the time and date now?"),
                                asList(
                                        ToolSpecification.builder()
                                                .name("current_time")
                                                .description("returns current time")
                                                .build(),
                                        ToolSpecification.builder()
                                                .name("current_date")
                                                .description("returns current date")
                                                .build()
                                ),
                                asList(
                                        ToolExecutionRequest.builder()
                                                .name("current_time") // 2 tokens
                                                .arguments("{}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .name("current_date") // 2 tokens
                                                .arguments("{}")
                                                .build()
                                ),
                                model
                        ),
                        arguments(
                                userMessage("What is the time and date now?"),
                                asList(
                                        ToolSpecification.builder()
                                                .name("get_current_time")
                                                .description("returns current time")
                                                .build(),
                                        ToolSpecification.builder()
                                                .name("get_current_date")
                                                .description("returns current date")
                                                .build()
                                ),
                                asList(
                                        ToolExecutionRequest.builder()
                                                .name("get_current_time") // 3 tokens
                                                .arguments("{}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .name("get_current_date") // 3 tokens
                                                .arguments("{}")
                                                .build()
                                ),
                                model
                        ),

                        // no arguments, 3 tools
                        arguments(
                                userMessage("What is the time and date and location?"),
                                asList(
                                        ToolSpecification.builder()
                                                .name("time")
                                                .description("returns current time")
                                                .build(),
                                        ToolSpecification.builder()
                                                .name("date")
                                                .description("returns current date")
                                                .build(),
                                        ToolSpecification.builder()
                                                .name("location")
                                                .description("returns current location")
                                                .build()
                                ),
                                asList(
                                        ToolExecutionRequest.builder()
                                                .name("time")
                                                .arguments("{}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .name("date")
                                                .arguments("{}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .name("location")
                                                .arguments("{}")
                                                .build()
                                ),
                                model
                        ),

                        // no arguments, 1 argument
                        arguments(
                                userMessage("What is the time in Munich and date now?"),
                                asList(
                                        ToolSpecification.builder()
                                                .name("time")
                                                .description("returns current time")
                                                .addParameter("city")
                                                .build(),
                                        ToolSpecification.builder()
                                                .name("date")
                                                .description("returns current date")
                                                .build()
                                ),
                                asList(
                                        ToolExecutionRequest.builder()
                                                .name("time")
                                                .arguments("{\"city\":\"Munich\"}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .name("date")
                                                .arguments("{}")
                                                .build()
                                ),
                                model
                        ),

                        // one argument, 2 different tools, different lengths of "arguments"
                        arguments(
                                userMessage("What is the time and date in Berlin now?"),
                                asList(
                                        ToolSpecification.builder()
                                                .name("current_time")
                                                .description("returns current time")
                                                .addParameter("city")
                                                .build(),
                                        ToolSpecification.builder()
                                                .name("current_date")
                                                .description("returns current date")
                                                .addParameter("city")
                                                .build()
                                ),
                                asList(
                                        ToolExecutionRequest.builder()
                                                .name("current_time")
                                                .arguments("{\"city\":\"Berlin\"}") // 5 tokens
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .name("current_date")
                                                .arguments("{\"city\":\"Berlin\"}") // 5 tokens
                                                .build()
                                ),
                                model
                        ),
                        arguments(
                                userMessage("What is the time and date in Pietramontecorvino now?"),
                                asList(
                                        ToolSpecification.builder()
                                                .name("current_time")
                                                .description("returns current time")
                                                .addParameter("city")
                                                .build(),
                                        ToolSpecification.builder()
                                                .name("current_date")
                                                .description("returns current date")
                                                .addParameter("city")
                                                .build()
                                ),
                                asList(
                                        ToolExecutionRequest.builder()
                                                .name("current_time")
                                                .arguments("{\"city\":\"Pietramontecorvino\"}") // 12 tokens
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .name("current_date")
                                                .arguments("{\"city\":\"Pietramontecorvino\"}") // 12 tokens
                                                .build()
                                ),
                                model
                        ),

                        // different tools, different lengths of argument values
                        arguments(
                                userMessage("What is the time in Berlin and date in Munich now?"),
                                asList(
                                        ToolSpecification.builder()
                                                .name("current_time")
                                                .description("returns current time")
                                                .addParameter("city")
                                                .build(),
                                        ToolSpecification.builder()
                                                .name("current_date")
                                                .description("returns current date")
                                                .addParameter("city")
                                                .build()
                                ),
                                asList(
                                        ToolExecutionRequest.builder()
                                                .name("current_time")
                                                .arguments("{\"city\":\"Berlin\"}") // 5 tokens
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .name("current_date")
                                                .arguments("{\"city\":\"Munich\"}") // 7 tokens
                                                .build()
                                ),
                                model
                        ),

                        // different tools, different lengths of "name", different lengths of argument values
                        arguments(
                                userMessage("What is the time in Berlin and date in Munich now?"),
                                asList(
                                        ToolSpecification.builder()
                                                .name("time")
                                                .description("returns current time")
                                                .addParameter("city")
                                                .build(),
                                        ToolSpecification.builder()
                                                .name("current_date")
                                                .description("returns current date")
                                                .addParameter("city")
                                                .build()
                                ),
                                asList(
                                        ToolExecutionRequest.builder()
                                                .name("time") // 1 tokens
                                                .arguments("{\"city\":\"Berlin\"}") // 5 tokens
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .name("current_date") // 2 tokens
                                                .arguments("{\"city\":\"Munich\"}") // 7 tokens
                                                .build()
                                ),
                                model
                        ),

                        // one argument, 4 tool requests
                        arguments(
                                userMessage("What is the time in Berlin, Munich, London and Paris now?"),
                                singletonList(
                                        ToolSpecification.builder()
                                                .name("time")
                                                .description("returns current time")
                                                .addParameter("city")
                                                .build()
                                ),
                                asList(
                                        ToolExecutionRequest.builder()
                                                .name("time")
                                                .arguments("{\"city\":\"Berlin\"}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .name("time")
                                                .arguments("{\"city\":\"Munich\"}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .name("time")
                                                .arguments("{\"city\":\"London\"}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .name("time")
                                                .arguments("{\"city\":\"Paris\"}")
                                                .build()
                                ),
                                model
                        ),

                        // two arguments, different lengths of "arguments"
                        arguments(
                                userMessage("What is the time and date in Berlin now?"),
                                asList(
                                        ToolSpecification.builder()
                                                .name("current_time")
                                                .description("returns current time")
                                                .addParameter("city")
                                                .addParameter("country")
                                                .build(),
                                        ToolSpecification.builder()
                                                .name("current_date")
                                                .description("returns current date")
                                                .addParameter("city")
                                                .addParameter("country")
                                                .build()
                                ),
                                asList(
                                        ToolExecutionRequest.builder()
                                                .name("current_time")
                                                .arguments("{\"country\":\"Germany\",\"city\":\"Berlin\"}") // 9 tokens
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .name("current_date") // 2 tokens
                                                .arguments("{\"country\":\"Germany\",\"city\":\"Berlin\"}") // 9 tokens
                                                .build()
                                ),
                                model
                        ),
                        arguments(
                                userMessage("What is the time and date in Pietramontecorvino now?"),
                                asList(
                                        ToolSpecification.builder()
                                                .name("current_time")
                                                .description("returns current time")
                                                .addParameter("city")
                                                .addParameter("country")
                                                .build(),
                                        ToolSpecification.builder()
                                                .name("current_date")
                                                .description("returns current date")
                                                .addParameter("city")
                                                .addParameter("country")
                                                .build()
                                ),
                                asList(
                                        ToolExecutionRequest.builder()
                                                .name("current_time")
                                                .arguments("{\"country\":\"Italy\",\"city\":\"Pietramontecorvino\"}") // 16 tokens
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .name("current_date")
                                                .arguments("{\"country\":\"Italy\",\"city\":\"Pietramontecorvino\"}") // 16 tokens
                                                .build()
                                ),
                                model
                        ),

                        // three arguments, different lengths of "arguments"
                        arguments(
                                userMessage("What is the time in Berlin and Pietramontecorvino in 24-hour format?"),
                                singletonList(
                                        ToolSpecification.builder()
                                                .name("current_time")
                                                .description("returns current time")
                                                .addParameter("city")
                                                .addParameter("country")
                                                .addParameter("format", enums("12", "24"))
                                                .build()
                                ),
                                asList(
                                        ToolExecutionRequest.builder()
                                                .name("current_time") // 2 tokens
                                                .arguments("{\"country\":\"Germany\",\"city\":\"Berlin\",\"format\":\"24\"}") // 13 tokens
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .name("current_time") // 2 tokens
                                                .arguments("{\"country\":\"Italy\",\"city\":\"Pietramontecorvino\",\"format\":\"24\"}") // 20 tokens
                                                .build()
                                ),
                                model
                        ),

                        // three tool execution requests, different tools and lengths of "arguments"
                        arguments(
                                userMessage("What is the time in Berlin and Pietramontecorvino in 24-hour format now and date in Munich?"),
                                asList(
                                        ToolSpecification.builder()
                                                .name("current_time")
                                                .description("returns current time")
                                                .addParameter("city")
                                                .addParameter("country")
                                                .addParameter("format", enums("12", "24"))
                                                .build(),
                                        ToolSpecification.builder()
                                                .name("current_date")
                                                .description("returns current date")
                                                .addParameter("city")
                                                .addParameter("country")
                                                .build()
                                ),
                                asList(
                                        ToolExecutionRequest.builder()
                                                .name("current_time")
                                                .arguments("{\"country\":\"Germany\",\"city\":\"Berlin\",\"format\": \"24\"}") // 14 tokens
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .name("current_time")
                                                .arguments("{\"country\":\"Italy\",\"city\":\"Pietramontecorvino\",\"format\": \"24\"}") // 21 tokens
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .name("current_date")
                                                .arguments("{\"country\":\"Germany\",\"city\":\"Munich\"}") // 11 tokens
                                                .build()
                                ),
                                model
                        )
                ));
    }
}