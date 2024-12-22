package dev.langchain4j.model.chat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.Response;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Make sure these dependencies are present in the module where this test class is extended:
 * <pre>
 *
 * <dependency>
 *     <groupId>dev.langchain4j</groupId>
 *     <artifactId>langchain4j-core</artifactId>
 *     <scope>test</scope>
 * </dependency>
 *
 * <dependency>
 *     <groupId>dev.langchain4j</groupId>
 *     <artifactId>langchain4j-core</artifactId>
 *     <classifier>tests</classifier>
 *     <type>test-jar</type>
 *     <scope>test</scope>
 * </dependency>
 *
 * <dependency>
 *     <groupId>org.mockito</groupId>
 *     <artifactId>mockito-core</artifactId>
 *     <scope>test</scope>
 * </dependency>
 *
 * <dependency>
 *     <groupId>org.mockito</groupId>
 *     <artifactId>mockito-junit-jupiter</artifactId>
 *     <scope>test</scope>
 * </dependency>
 *
 * </pre>
 */
public abstract class StreamingChatModelListenerIT {

    protected abstract StreamingChatLanguageModel createModel(ChatModelListener listener);

    protected abstract String modelName();

    protected Double temperature() {
        return 0.7;
    }

    protected Double topP() {
        return 1.0;
    }

    protected Integer maxTokens() {
        return 7;
    }

    protected abstract StreamingChatLanguageModel createFailingModel(ChatModelListener listener);

    protected abstract Class<? extends Exception> expectedExceptionClass();

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
                assertThat(responseContext.attributes()).containsEntry("id", "12345");
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                fail("onError() must not be called. Exception: " + errorContext.error().getMessage());
            }
        };

        StreamingChatLanguageModel model = createModel(listener);

        UserMessage userMessage = UserMessage.from("hello");

        ToolSpecification toolSpecification = null;
        if (supportsTools()) {
            toolSpecification = ToolSpecification.builder()
                    .name("add")
                    .addParameter("a", INTEGER)
                    .addParameter("b", INTEGER)
                    .build();
        }

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        if (supportsTools()) {
            model.generate(singletonList(userMessage), singletonList(toolSpecification), handler);
        } else {
            model.generate(singletonList(userMessage), handler);
        }
        AiMessage aiMessage = handler.get().content();

        // then
        ChatModelRequest request = requestReference.get();
        assertThat(request.model()).isEqualTo(modelName());
        assertThat(request.temperature()).isCloseTo(temperature(), Percentage.withPercentage(1));
        assertThat(request.topP()).isEqualTo(topP());
        assertThat(request.maxTokens()).isEqualTo(maxTokens());
        assertThat(request.messages()).containsExactly(userMessage);
        if (supportsTools()) {
            assertThat(request.toolSpecifications()).containsExactly(toolSpecification);
        }

        ChatModelResponse response = responseReference.get();
        if (assertResponseId()) {
            assertThat(response.id()).isNotBlank();
        }
        if (assertResponseModel()) {
            assertThat(response.model()).isNotBlank();
        }
        if (assertTokenUsage()) {
            assertThat(response.tokenUsage().inputTokenCount()).isGreaterThan(0);
            assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(0);
            assertThat(response.tokenUsage().totalTokenCount()).isGreaterThan(0);
        }
        if (assertFinishReason()) {
            assertThat(response.finishReason()).isNotNull();
        }
        assertThat(response.aiMessage()).isEqualTo(aiMessage);
    }

    protected boolean supportsTools() {
        return true;
    }

    protected boolean assertResponseId() {
        return true;
    }

    protected boolean assertResponseModel() {
        return true;
    }

    protected boolean assertTokenUsage() {
        return true;
    }

    protected boolean assertFinishReason() {
        return true;
    }

    @Test
    protected void should_listen_error() throws Exception {

        // given
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
                assertThat(errorContext.attributes()).containsEntry("id", "12345");
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
