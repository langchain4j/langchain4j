package dev.langchain4j.model.azure;

import com.azure.core.exception.ClientAuthenticationException;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatModelListenerIT;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.chat.listener.*;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

class AzureOpenAiStreamingChatModelListenerIT extends StreamingChatModelListenerIT {

    @Override
    protected StreamingChatLanguageModel createModel(ChatModelListener listener) {
        return AzureOpenAiStreamingChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(modelName())
                .temperature(temperature())
                .topP(topP())
                .maxTokens(maxTokens())
                .logRequestsAndResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return "gpt-4o-mini";
    }

    @Override
    protected StreamingChatLanguageModel createFailingModel(ChatModelListener listener) {
        return AzureOpenAiStreamingChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey("banana")
                .deploymentName(modelName())
                .maxRetries(1)
                .logRequestsAndResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return ClientAuthenticationException.class;
    }

    @Test
    public void should_listen_request_and_response() {

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
        double topP = 1.0;
        int maxTokens = 7;

        StreamingChatLanguageModel model = createModel(listener);

        UserMessage userMessage = UserMessage.from("hello");

        ToolSpecification toolSpecification = ToolSpecification.builder()
            .name("add")
            .addParameter("a", INTEGER)
            .addParameter("b", INTEGER)
            .build();

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), singletonList(toolSpecification), handler);
        AiMessage aiMessage = handler.get().content();

        // then
        ChatModelRequest request = requestReference.get();
        assertThat(request.model()).isEqualTo(modelName());
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
    public void should_listen_error() throws Exception {

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
                assertThat(errorContext.partialResponse()).isNull(); // can be non-null if it fails in the middle of streaming
                assertThat(errorContext.attributes().get("id")).isEqualTo("12345");
            }
        };

        StreamingChatLanguageModel model = createFailingModel(listener);

        String userMessage = "this message will fail";

        CompletableFuture<Throwable> future = new CompletableFuture<>();
        StreamingResponseHandler<AiMessage> handler = new StreamingResponseHandler<AiMessage>() {

            @Override
            public void onNext(String token) {
                fail("onNext() must not be called");
            }

            @Override
            public void onError(Throwable error) {
                future.complete(error);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                fail("onComplete() must not be called");
            }
        };

        // when
        model.generate(userMessage, handler);
        Throwable throwable = future.get(5, SECONDS);

        // then
        assertThat(throwable).isExactlyInstanceOf(expectedExceptionClass());

        assertThat(errorReference.get()).isSameAs(throwable);
    }
}
