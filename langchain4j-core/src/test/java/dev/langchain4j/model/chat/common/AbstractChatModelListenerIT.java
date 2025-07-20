package dev.langchain4j.model.chat.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;

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
public abstract class AbstractChatModelListenerIT {

    protected abstract ChatModel createModel(ChatModelListener listener);

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

    protected abstract ChatModel createFailingModel(ChatModelListener listener);

    protected abstract Class<? extends Exception> expectedExceptionClass();

    @Test
    void should_listen_request_and_response() {

        // given
        AtomicReference<ChatRequest> chatRequestReference = new AtomicReference<>();
        AtomicInteger onRequestInvocations = new AtomicInteger();

        AtomicReference<ChatResponse> chatResponseReference = new AtomicReference<>();
        AtomicInteger onResponseInvocations = new AtomicInteger();
        AtomicReference<ChatModel> modelReference = new AtomicReference<>();

        ChatModelListener listener = new ChatModelListener() {

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                chatRequestReference.set(requestContext.chatRequest());
                onRequestInvocations.incrementAndGet();

                assertThat(requestContext.modelProvider())
                        .isNotNull()
                        .isEqualTo(modelReference.get().provider());

                requestContext.attributes().put("id", "12345");
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                chatResponseReference.set(responseContext.chatResponse());
                onResponseInvocations.incrementAndGet();

                assertThat(responseContext.chatRequest()).isEqualTo(chatRequestReference.get());

                assertThat(responseContext.modelProvider())
                        .isNotNull()
                        .isEqualTo(modelReference.get().provider());

                assertThat(responseContext.attributes()).containsEntry("id", "12345");
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                fail("onError() must not be called. Exception: "
                        + errorContext.error().getMessage());
            }
        };

        ChatModel model = createModel(listener);
        modelReference.set(model);

        UserMessage userMessage = UserMessage.from("hello");

        ChatRequest.Builder chatRequestBuilder = ChatRequest.builder().messages(userMessage);

        ToolSpecification toolSpecification = null;
        if (supportsTools()) {
            toolSpecification = ToolSpecification.builder()
                    .name("add")
                    .parameters(JsonObjectSchema.builder()
                            .addIntegerProperty("a")
                            .addIntegerProperty("b")
                            .build())
                    .build();
            chatRequestBuilder.toolSpecifications(toolSpecification);
        }

        ChatRequest chatRequest = chatRequestBuilder.build();

        // when
        AiMessage aiMessage = model.chat(chatRequest).aiMessage();

        // then
        ChatRequest observedChatRequest = chatRequestReference.get();
        assertThat(observedChatRequest.messages()).containsExactly(userMessage);

        ChatRequestParameters parameters = observedChatRequest.parameters();
        assertThat(parameters.modelName()).isEqualTo(modelName());
        assertThat(parameters.temperature()).isCloseTo(temperature(), Percentage.withPercentage(1));
        assertThat(parameters.topP()).isEqualTo(topP());
        assertThat(parameters.maxOutputTokens()).isEqualTo(maxTokens());
        if (supportsTools()) {
            assertThat(parameters.toolSpecifications()).containsExactly(toolSpecification);
        }

        assertThat(onRequestInvocations).hasValue(1);

        ChatResponse chatResponse = chatResponseReference.get();
        assertThat(chatResponse.aiMessage()).isEqualTo(aiMessage);

        ChatResponseMetadata metadata = chatResponse.metadata();
        if (assertResponseId()) {
            assertThat(metadata.id()).isNotBlank();
        }
        assertThat(metadata.modelName()).isNotBlank();
        assertThat(metadata.tokenUsage().inputTokenCount()).isGreaterThan(0);
        assertThat(metadata.tokenUsage().outputTokenCount()).isGreaterThan(0);
        assertThat(metadata.tokenUsage().totalTokenCount()).isGreaterThan(0);
        if (assertFinishReason()) {
            assertThat(metadata.finishReason()).isNotNull();
        }

        assertThat(onResponseInvocations).hasValue(1);
    }

    protected boolean supportsTools() {
        return true;
    }

    protected boolean assertResponseId() {
        return true;
    }

    protected boolean assertFinishReason() {
        return true;
    }

    @Test
    void should_listen_error() {

        // given
        AtomicReference<ChatRequest> chatRequestReference = new AtomicReference<>();
        AtomicInteger onRequestInvocations = new AtomicInteger();

        AtomicReference<Throwable> errorReference = new AtomicReference<>();
        AtomicInteger onErrorInvocations = new AtomicInteger();
        AtomicReference<ChatModel> modelReference = new AtomicReference<>();

        ChatModelListener listener = new ChatModelListener() {

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                chatRequestReference.set(requestContext.chatRequest());
                onRequestInvocations.incrementAndGet();

                assertThat(requestContext.modelProvider())
                        .isNotNull()
                        .isEqualTo(modelReference.get().provider());

                requestContext.attributes().put("id", "12345");
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                fail("onResponse() must not be called");
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                errorReference.set(errorContext.error());
                onErrorInvocations.incrementAndGet();

                assertThat(errorContext.chatRequest()).isEqualTo(chatRequestReference.get());

                assertThat(errorContext.modelProvider())
                        .isNotNull()
                        .isEqualTo(modelReference.get().provider());

                assertThat(errorContext.attributes()).containsEntry("id", "12345");
            }
        };

        ChatModel model = createFailingModel(listener);
        modelReference.set(model);

        String userMessage = "this message will fail";

        // when
        Throwable thrown = null;
        try {
            model.chat(userMessage);
        } catch (Exception e) {
            thrown = e;
        }

        // then
        Throwable error = errorReference.get();
        assertThat(error).isExactlyInstanceOf(expectedExceptionClass());

        assertThat(thrown == error || thrown.getCause() == error).isTrue(); // TODO fix discrepancy, do not wrap

        assertThat(onRequestInvocations).hasValue(1);
        assertThat(onErrorInvocations).hasValue(1);
    }
}
