package dev.langchain4j.model.azure;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ChatCompletionsJsonResponseFormat;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.toolExecutionResultMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class AzureOpenAiStreamingChatModelIT {

    public long STREAMING_TIMEOUT = 120;

    @ParameterizedTest(name = "Deployment name {0} using {1} with async client set to {2}")
    @CsvSource({
            "gpt-4o,        gpt-4o, true",
            "gpt-4o,        gpt-4o, false"
    })
    void should_stream_answer(String deploymentName, String gptVersion, boolean useAsyncClient) throws Exception {

        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        StreamingChatLanguageModel model = AzureOpenAiStreamingChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .useAsyncClient(useAsyncClient)
                .tokenizer(new AzureOpenAiTokenizer(gptVersion))
                .logRequestsAndResponses(true)
                .build();

        model.chat("What is the capital of France?", new StreamingChatResponseHandler() {

            private final StringBuilder answerBuilder = new StringBuilder();

            @Override
            public void onPartialResponse(String partialResponse) {
                answerBuilder.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureAnswer.complete(answerBuilder.toString());
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureAnswer.completeExceptionally(error);
                futureResponse.completeExceptionally(error);
            }
        });

        String answer = futureAnswer.get(STREAMING_TIMEOUT, SECONDS);
        ChatResponse response = futureResponse.get(STREAMING_TIMEOUT, SECONDS);

        assertThat(answer).contains("Paris");
        assertThat(response.aiMessage().text()).isEqualTo(answer);

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(14);
        assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().totalTokenCount())
                .isEqualTo(response.tokenUsage().inputTokenCount() + response.tokenUsage().outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest(name = "Deployment name {0} using {1} with custom async client set to {2} ")
    @CsvSource({
            "gpt-4o,        gpt-4o, true",
            "gpt-4o,        gpt-4o, false"
    })
    void should_custom_models_work(String deploymentName, String gptVersion, boolean useCustomAsyncClient) throws Exception {

        OpenAIAsyncClient asyncClient = null;
        OpenAIClient client = null;
        if (useCustomAsyncClient) {
            asyncClient = InternalAzureOpenAiHelper.setupAsyncClient(System.getenv("AZURE_OPENAI_ENDPOINT"), gptVersion, System.getenv("AZURE_OPENAI_KEY"), Duration.ofSeconds(30), 5, null, true, null, null);
        } else {
            client = InternalAzureOpenAiHelper.setupSyncClient(System.getenv("AZURE_OPENAI_ENDPOINT"), gptVersion, System.getenv("AZURE_OPENAI_KEY"), Duration.ofSeconds(30), 5, null, true, null, null);
        }

        StreamingChatLanguageModel model = AzureOpenAiStreamingChatModel.builder()
                .openAIAsyncClient(asyncClient)
                .openAIClient(client)
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .tokenizer(new AzureOpenAiTokenizer(gptVersion))
                .logRequestsAndResponses(true)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the capital of France?", handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).contains("Paris");

        assertThat(response.tokenUsage().inputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().totalTokenCount())
                .isEqualTo(response.tokenUsage().inputTokenCount() + response.tokenUsage().outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest(name = "Deployment name {0}")
    @ValueSource(strings = {"gpt-4o"})
    void should_use_json_format(String deploymentName) {

        StreamingChatLanguageModel model = AzureOpenAiStreamingChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .responseFormat(new ChatCompletionsJsonResponseFormat())
                .temperature(0.0)
                .maxTokens(50)
                .logRequestsAndResponses(true)
                .build();

        String userMessage = "Return JSON with two fields: name and surname of Klaus Heisler.";

        String expectedJson = "{\"name\": \"Klaus\", \"surname\": \"Heisler\"}";

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(userMessage, handler);
        ChatResponse response = handler.get();

        assertThat(response.aiMessage().text()).isEqualToIgnoringWhitespace(expectedJson);
    }

    @ParameterizedTest(name = "Deployment name {0} using {1}")
    @CsvSource({
            "gpt-4o,        gpt-4o"
    })
    void should_execute_tool_forcefully_then_stream_answer(String deploymentName, String gptVersion) throws Exception {

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        StreamingChatLanguageModel model = AzureOpenAiStreamingChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .tokenizer(new AzureOpenAiTokenizer(gptVersion))
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = userMessage("Two plus two?");

        String toolName = "calculator";

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name(toolName)
                .description("returns a sum of two numbers")
                .addParameter("first", INTEGER)
                .addParameter("second", INTEGER)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(toolSpecification)
                        .toolChoice(REQUIRED)
                        .build())
                .build();

        model.chat(request, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                Exception e = new IllegalStateException("partialResponse() should never be called when tool is executed");
                futureResponse.completeExceptionally(e);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        ChatResponse response = futureResponse.get(STREAMING_TIMEOUT, SECONDS);

        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNull();

        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        assertThat(response.tokenUsage().inputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().totalTokenCount())
                .isEqualTo(response.tokenUsage().inputTokenCount() + response.tokenUsage().outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);

        ToolExecutionResultMessage toolExecutionResultMessage = toolExecutionResultMessage(toolExecutionRequest, "four");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        CompletableFuture<ChatResponse> futureResponse2 = new CompletableFuture<>();

        model.chat(messages, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse2.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse2.completeExceptionally(error);
            }
        });

        ChatResponse response2 = futureResponse2.get(STREAMING_TIMEOUT, SECONDS);
        AiMessage aiMessage2 = response2.aiMessage();

        // then
        assertThat(aiMessage2.text()).contains("four");
        assertThat(aiMessage2.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage2 = response2.tokenUsage();
        assertThat(tokenUsage2.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.totalTokenCount())
                .isEqualTo(tokenUsage2.inputTokenCount() + tokenUsage2.outputTokenCount());

        assertThat(response2.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest(name = "Deployment name {0} using {1}")
    @CsvSource({
            "gpt-4o,        gpt-4o"
    })
    void should_call_three_functions_in_parallel(String deploymentName, String gptVersion) throws Exception {

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        StreamingChatLanguageModel model = AzureOpenAiStreamingChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .tokenizer(new AzureOpenAiTokenizer(gptVersion))
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = userMessage("Give three numbers, ordered by size: the sum of two plus two, the square of four, and finally the cube of eight.");

        List<ToolSpecification> toolSpecifications = asList(
                ToolSpecification.builder()
                        .name("sum")
                        .description("returns a sum of two numbers")
                        .parameters(JsonObjectSchema.builder()
                                .addIntegerProperty("first")
                                .addIntegerProperty("second")
                                .required("first", "second")
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("square")
                        .description("returns the square of one number")
                        .parameters(JsonObjectSchema.builder()
                                .addIntegerProperty("number")
                                .required("number")
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("cube")
                        .description("returns the cube of one number")
                        .parameters(JsonObjectSchema.builder()
                                .addIntegerProperty("number")
                                .required("number")
                                .build())
                        .build()
        );

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(toolSpecifications)
                .build();

        model.chat(chatRequest, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                Exception e = new IllegalStateException("onPartialResponse() should never be called when tool is executed");
                futureResponse.completeExceptionally(e);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        ChatResponse response = futureResponse.get(STREAMING_TIMEOUT, SECONDS);

        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNull();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(userMessage);
        messages.add(aiMessage);
        assertThat(aiMessage.toolExecutionRequests()).hasSize(3);
        for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
            assertThat(toolExecutionRequest.name()).isNotEmpty();
            ToolExecutionResultMessage toolExecutionResultMessage;
            if (toolExecutionRequest.name().equals("sum")) {
                assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");
                toolExecutionResultMessage = toolExecutionResultMessage(toolExecutionRequest, "4");
            } else if (toolExecutionRequest.name().equals("square")) {
                assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"number\": 4}");
                toolExecutionResultMessage = toolExecutionResultMessage(toolExecutionRequest, "16");
            } else if (toolExecutionRequest.name().equals("cube")) {
                assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"number\": 8}");
                toolExecutionResultMessage = toolExecutionResultMessage(toolExecutionRequest, "512");
            } else {
                throw new AssertionError("Unexpected tool name: " + toolExecutionRequest.name());
            }
            messages.add(toolExecutionResultMessage);
        }
        CompletableFuture<ChatResponse> futureResponse2 = new CompletableFuture<>();

        model.chat(messages, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse2.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse2.completeExceptionally(error);
            }
        });

        ChatResponse response2 = futureResponse2.get(STREAMING_TIMEOUT, SECONDS);
        AiMessage aiMessage2 = response2.aiMessage();

        // then
        assertThat(aiMessage2.text()).contains("4", "16", "512");
        assertThat(aiMessage2.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage2 = response2.tokenUsage();
        assertThat(tokenUsage2.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.totalTokenCount())
                .isEqualTo(tokenUsage2.inputTokenCount() + tokenUsage2.outputTokenCount());

        assertThat(response2.finishReason()).isEqualTo(STOP);
    }

    @Test
    void tools_should_work_without_tokenizer() {

        // given
        StreamingChatLanguageModel model = AzureOpenAiStreamingChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName("gpt-4o")
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is 2+2?");

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("calculator")
                .description("returns a sum of two numbers")
                .parameters(JsonObjectSchema.builder()
                        .addIntegerProperty("first")
                        .addIntegerProperty("second")
                        .required("first", "second")
                        .build())
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(toolSpecification)
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(chatRequest, handler);

        ChatResponse response = handler.get();

        assertThat(response.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(response.tokenUsage()).isNotNull();
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_AZURE_OPENAI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
