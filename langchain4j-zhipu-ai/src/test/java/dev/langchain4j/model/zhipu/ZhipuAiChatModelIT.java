package dev.langchain4j.model.zhipu;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.*;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.zhipu.chat.ChatCompletionModel;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
class ZhipuAiChatModelIT {
    private static final String apiKey = System.getenv("ZHIPU_API_KEY");

    ZhipuAiChatModel chatModel = ZhipuAiChatModel.builder()
            .apiKey(apiKey)
            .logRequests(true)
            .logResponses(true)
            .maxRetries(1)
            .callTimeout(Duration.ofSeconds(60))
            .connectTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(60))
            .readTimeout(Duration.ofSeconds(60))
            .build();

    ToolSpecification calculator = ToolSpecification.builder()
            .name("calculator")
            .description("returns a sum of two numbers")
            .addParameter("first", INTEGER)
            .addParameter("second", INTEGER)
            .build();

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {

        // given
        UserMessage userMessage = userMessage("中国首都在哪里");

        // when
        Response<AiMessage> response = chatModel.generate(userMessage);

        // then
        assertThat(response.content().text()).contains("北京");

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_sensitive_words_answer() {
        ZhipuAiChatModel model = ZhipuAiChatModel.builder()
                .apiKey(apiKey + 1)
                .logRequests(true)
                .logResponses(true)
                .maxRetries(1)
                .callTimeout(Duration.ofSeconds(60))
                .connectTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .readTimeout(Duration.ofSeconds(60))
                .build();
        // given
        UserMessage userMessage = userMessage("this message will fail");

        // when
        Response<AiMessage> response = model.generate(userMessage);

        assertThat(response.content().text()).isEqualTo("Authorization Token非法，请确认Authorization Token正确传递。");

        assertThat(response.finishReason()).isEqualTo(OTHER);
    }

    @Test
    void should_execute_a_tool_then_answer() {

        // given
        UserMessage userMessage = userMessage("2+2=?");
        List<ToolSpecification> toolSpecifications = singletonList(calculator);

        // when
        Response<AiMessage> response = chatModel.generate(singletonList(userMessage), toolSpecifications);

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "4");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        Response<AiMessage> secondResponse = chatModel.generate(messages);

        // then
        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }


    ToolSpecification currentTime = ToolSpecification.builder()
            .name("currentTime")
            .description("currentTime")
            .build();

    @Test
    void should_execute_get_current_time_tool_and_then_answer() {
        // given
        UserMessage userMessage = userMessage("What's the time now?");
        List<ToolSpecification> toolSpecifications = singletonList(currentTime);

        // when
        Response<AiMessage> response = chatModel.generate(singletonList(userMessage), toolSpecifications);

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("currentTime");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "2024-04-23 12:00:20");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        Response<AiMessage> secondResponse = chatModel.generate(messages);

        // then
        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("12:00:20");
        assertThat(secondAiMessage.text()).contains("2024");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }


    @Test
    void should_listen_request_and_response() {

        // given
        AtomicReference<ChatModelRequest> requestReference = new AtomicReference<>();
        AtomicReference<ChatModelResponse> responseReference = new AtomicReference<>();

        ChatModelListener listener = new ChatModelListener() {

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                requestReference.set(requestContext.request());
                requestContext.attributes().put("id", "12345");
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                responseReference.set(responseContext.response());
                assertThat(responseContext.request()).isSameAs(requestReference.get());
                assertThat(responseContext.attributes().get("id")).isEqualTo("12345");
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                fail("onError() must not be called");
            }
        };

        double temperature = 0.7;
        double topP = 0.7;
        int maxTokens = 7;

        ZhipuAiChatModel model = ZhipuAiChatModel.builder()
                .apiKey(apiKey)
                .topP(topP)
                .maxToken(maxTokens)
                .temperature(temperature)
                .logRequests(true)
                .logResponses(true)
                .maxRetries(1)
                .listeners(singletonList(listener))
                .callTimeout(Duration.ofSeconds(60))
                .connectTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .readTimeout(Duration.ofSeconds(60))
                .build();

        UserMessage userMessage = UserMessage.from("hello");

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("add")
                .addParameter("a", INTEGER)
                .addParameter("b", INTEGER)
                .build();

        // when
        AiMessage aiMessage = model.generate(singletonList(userMessage), singletonList(toolSpecification)).content();

        // then
        ChatModelRequest request = requestReference.get();
        assertThat(request.temperature()).isEqualTo(temperature);
        assertThat(request.topP()).isEqualTo(topP);
        assertThat(request.maxTokens()).isEqualTo(maxTokens);
        assertThat(request.messages()).containsExactly(userMessage);
        assertThat(request.toolSpecifications()).containsExactly(toolSpecification);

        ChatModelResponse response = responseReference.get();
        assertThat(response.id()).isNotBlank();
        assertThat(response.model()).isNotBlank();
        assertThat(response.tokenUsage().inputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().totalTokenCount()).isGreaterThan(0);
        assertThat(response.finishReason()).isNotNull();
        assertThat(response.aiMessage()).isEqualTo(aiMessage);
    }

    @Test
    void should_listen_error() {

        AtomicReference<ChatModelRequest> requestReference = new AtomicReference<>();
        AtomicReference<Throwable> errorReference = new AtomicReference<>();

        ChatModelListener listener = new ChatModelListener() {

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                requestReference.set(requestContext.request());
                requestContext.attributes().put("id", "12345");
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                fail("onResponse() must not be called");
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                errorReference.set(errorContext.error());
                assertThat(errorContext.request()).isSameAs(requestReference.get());
                assertThat(errorContext.partialResponse()).isNull();
                assertThat(errorContext.attributes().get("id")).isEqualTo("12345");
            }
        };

        ZhipuAiChatModel model = ZhipuAiChatModel.builder()
                .apiKey(apiKey + 1)
                .logRequests(true)
                .logResponses(true)
                .maxRetries(1)
                .listeners(singletonList(listener))
                .callTimeout(Duration.ofSeconds(60))
                .connectTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .readTimeout(Duration.ofSeconds(60))
                .build();

        String userMessage = "this message will fail";
        model.generate(userMessage);

        // then
        Throwable throwable = errorReference.get();
        assertThat(throwable).isInstanceOf(ZhipuAiException.class);
        assertThat(throwable).hasMessageContaining("Authorization Token非法，请确认Authorization Token正确传递。");
    }

    @Test
    public void should_send_multimodal_image_data_and_receive_response() {
        ChatLanguageModel model = ZhipuAiChatModel.builder()
                .apiKey(apiKey)
                .model(ChatCompletionModel.GLM_4V)
                .callTimeout(Duration.ofSeconds(60))
                .connectTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .readTimeout(Duration.ofSeconds(60))
                .build();

        Response<AiMessage> response = model.generate(multimodalChatMessagesWithImageData());

        assertThat(response.content().text()).containsIgnoringCase("parrot");
        assertThat(response.content().text()).endsWith("That's all!");
    }

    public static List<ChatMessage> multimodalChatMessagesWithImageData() {
        Image image = Image.builder()
                .base64Data(multimodalImageData())
                .build();
        ImageContent imageContent = ImageContent.from(image);
        TextContent textContent = TextContent.from("What animal is in the picture? When you're done, end with \"That's all!\".");
        return Collections.singletonList(UserMessage.from(imageContent, textContent));
    }

    public static String multimodalImageData() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = ZhipuAiChatModelIT.class.getResourceAsStream("/parrot.jpg")) {
            assertThat(in).isNotNull();
            byte[] data = new byte[512];
            int n;
            while ((n = in.read(data)) != -1) {
                buffer.write(data, 0, n);
            }
        } catch (IOException e) {
            Assertions.fail("", e.getMessage());
        }

        return Base64.getEncoder().encodeToString(buffer.toByteArray());
    }
}