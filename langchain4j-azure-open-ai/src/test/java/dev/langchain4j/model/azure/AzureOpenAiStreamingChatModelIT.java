package dev.langchain4j.model.azure;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ChatCompletionsJsonResponseFormat;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class AzureOpenAiStreamingChatModelIT {

    Logger logger = LoggerFactory.getLogger(AzureOpenAiStreamingChatModelIT.class);

    @ParameterizedTest(name = "Deployment name {0} using {1} with async client set to {2}")
    @CsvSource({
            "gpt-35-turbo, gpt-3.5-turbo, true",
            "gpt-35-turbo, gpt-3.5-turbo, false",
            "gpt-4,        gpt-4, true",
            "gpt-4,        gpt-4, false"
    })
    void should_stream_answer(String deploymentName, String gptVersion, boolean useAsyncClient) throws Exception {

        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        StreamingChatLanguageModel model = AzureOpenAiStreamingChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .useAsyncClient(useAsyncClient)
                .tokenizer(new OpenAiTokenizer(gptVersion))
                .logRequestsAndResponses(true)
                .build();

        model.generate("What is the capital of France?", new StreamingResponseHandler<AiMessage>() {

            private final StringBuilder answerBuilder = new StringBuilder();

            @Override
            public void onNext(String token) {
                logger.info("onNext: '" + token + "'");
                answerBuilder.append(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                logger.info("onComplete: '" + response + "'");
                futureAnswer.complete(answerBuilder.toString());
                futureResponse.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                futureAnswer.completeExceptionally(error);
                futureResponse.completeExceptionally(error);
            }
        });

        String answer = futureAnswer.get(30, SECONDS);
        Response<AiMessage> response = futureResponse.get(30, SECONDS);

        assertThat(answer).contains("Paris");
        assertThat(response.content().text()).isEqualTo(answer);

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(14);
        assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().totalTokenCount())
                .isEqualTo(response.tokenUsage().inputTokenCount() + response.tokenUsage().outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest(name = "Deployment name {0} using {1} with custom async client set to {2} ")
    @CsvSource({
            "gpt-35-turbo, gpt-3.5-turbo, true",
            "gpt-35-turbo, gpt-3.5-turbo, false",
            "gpt-4,        gpt-4, true",
            "gpt-4,        gpt-4, false"
    })
    void should_custom_models_work(String deploymentName, String gptVersion, boolean useCustomAsyncClient) throws Exception {

        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();
        OpenAIAsyncClient asyncClient = null;
        OpenAIClient client = null;
        if(useCustomAsyncClient) {
            asyncClient = InternalAzureOpenAiHelper.setupAsyncClient(System.getenv("AZURE_OPENAI_ENDPOINT"), gptVersion, System.getenv("AZURE_OPENAI_KEY"), Duration.ofSeconds(30), 5, null, true);
        } else {
            client = InternalAzureOpenAiHelper.setupSyncClient(System.getenv("AZURE_OPENAI_ENDPOINT"), gptVersion, System.getenv("AZURE_OPENAI_KEY"), Duration.ofSeconds(30), 5, null, true);
        }

        StreamingChatLanguageModel model = AzureOpenAiStreamingChatModel.builder()
                .openAIAsyncClient(asyncClient)
                .openAIClient(client)
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .tokenizer(new OpenAiTokenizer(gptVersion))
                .logRequestsAndResponses(true)
                .build();
        model.generate("What is the capital of France?", new StreamingResponseHandler<AiMessage>() {

            private final StringBuilder answerBuilder = new StringBuilder();

            @Override
            public void onNext(String token) {
                logger.info("onNext: '" + token + "'");
                answerBuilder.append(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                logger.info("onComplete: '" + response + "'");
                futureAnswer.complete(answerBuilder.toString());
                futureResponse.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                futureAnswer.completeExceptionally(error);
                futureResponse.completeExceptionally(error);
            }
        });

        String answer = futureAnswer.get(30, SECONDS);
        Response<AiMessage> response = futureResponse.get(30, SECONDS);

        assertThat(answer).contains("Paris");
        assertThat(response.content().text()).isEqualTo(answer);

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(14);
        assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().totalTokenCount())
                .isEqualTo(response.tokenUsage().inputTokenCount() + response.tokenUsage().outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest(name = "Deployment name {0}")
    @ValueSource(strings = {"gpt-35-turbo", "gpt-4"})
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

        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();

        assertThat(response.content().text()).isEqualToIgnoringWhitespace(expectedJson);
    }

    @ParameterizedTest(name = "Deployment name {0} using {1}")
    @CsvSource({
            "gpt-35-turbo, gpt-3.5-turbo",
            "gpt-4,        gpt-4"
    })
    void should_return_tool_execution_request(String deploymentName, String gptVersion) throws Exception {

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("calculator")
                .description("returns a sum of two numbers")
                .addParameter("first", INTEGER)
                .addParameter("second", INTEGER)
                .build();

        UserMessage userMessage = userMessage("Two plus two?");

        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        StreamingChatLanguageModel model = AzureOpenAiStreamingChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .tokenizer(new OpenAiTokenizer(gptVersion))
                .logRequestsAndResponses(true)
                .build();

        model.generate(singletonList(userMessage), singletonList(toolSpecification), new StreamingResponseHandler<AiMessage>() {

            @Override
            public void onNext(String token) {
                logger.info("onNext: '" + token + "'");
                Exception e = new IllegalStateException("onNext() should never be called when tool is executed");
                futureResponse.completeExceptionally(e);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                logger.info("onComplete: '" + response + "'");
                futureResponse.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        Response<AiMessage> response = futureResponse.get(30, SECONDS);

        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();

        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(53);
        assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().totalTokenCount())
                .isEqualTo(response.tokenUsage().inputTokenCount() + response.tokenUsage().outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);
    }
}
