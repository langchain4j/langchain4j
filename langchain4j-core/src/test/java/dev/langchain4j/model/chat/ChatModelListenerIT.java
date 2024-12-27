package dev.langchain4j.model.chat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static java.util.Collections.singletonList;
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
public abstract class ChatModelListenerIT {

    protected abstract ChatLanguageModel createModel(ChatModelListener listener);

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

    protected abstract ChatLanguageModel createFailingModel(ChatModelListener listener);

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

        ChatLanguageModel model = createModel(listener);

        UserMessage userMessage = UserMessage.from("hello");

        ToolSpecification toolSpecification = null;
        if (supportToolCalls()) {
            toolSpecification = ToolSpecification.builder()
                    .name("add")
                    .addParameter("a", INTEGER)
                    .addParameter("b", INTEGER)
                    .build();
        }

        // when
        AiMessage aiMessage;
        if (supportToolCalls()) {
            aiMessage = model.generate(singletonList(userMessage), singletonList(toolSpecification)).content();
        } else {
            aiMessage = model.generate(singletonList(userMessage)).content();
        }

        // then
        ChatModelRequest request = requestReference.get();
        assertThat(request.model()).isEqualTo(modelName());
        assertThat(request.temperature()).isCloseTo(temperature(), Percentage.withPercentage(1));
        assertThat(request.topP()).isEqualTo(topP());
        assertThat(request.maxTokens()).isEqualTo(maxTokens());
        assertThat(request.messages()).containsExactly(userMessage);
        if (supportToolCalls()) {
            assertThat(request.toolSpecifications()).containsExactly(toolSpecification);
        }

        ChatModelResponse response = responseReference.get();
        if (assertResponseId()) {
            assertThat(response.id()).isNotBlank();
        }
        assertThat(response.model()).isNotBlank();
        assertThat(response.tokenUsage().inputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().totalTokenCount()).isGreaterThan(0);
        if (assertFinishReason()) {
            assertThat(response.finishReason()).isNotNull();
        }
        assertThat(response.aiMessage()).isEqualTo(aiMessage);
    }

    protected boolean supportToolCalls() {
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
                assertThat(errorContext.attributes()).containsEntry("id", "12345");
            }
        };

        ChatLanguageModel model = createFailingModel(listener);

        String userMessage = "this message will fail";

        // when
        Throwable thrown = null;
        try {
            model.generate(userMessage);
        } catch (Exception e) {
            thrown = e;
        }

        // then
        Throwable error = errorReference.get();
        assertThat(error).isExactlyInstanceOf(expectedExceptionClass());

        assertThat(thrown == error || thrown.getCause() == error).isTrue(); // TODO fix discrepancy, do not wrap
    }
}
