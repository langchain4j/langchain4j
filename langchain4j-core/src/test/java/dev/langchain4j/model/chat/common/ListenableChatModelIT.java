package dev.langchain4j.model.chat.common;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.ListenableChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public abstract class ListenableChatModelIT { // TODO?

    // TODO test streaming as well

    protected abstract ListenableChatModel createModel(ChatModelListener listener);

    protected abstract String modelName();

    protected Double temperature() {
        return 0.7;
    }

    protected Double topP() {
        return 1.0;
    }

    protected Integer maxOutputTokens() {
        return 7;
    }

    protected abstract ListenableChatModel createFailingModel(ChatModelListener listener);

    protected abstract Class<? extends Exception> expectedExceptionClass();

    @Test
    void should_listen_request_and_response() {

        // given
        AtomicReference<ChatRequest> requestReference = new AtomicReference<>();
        AtomicReference<ChatResponse> responseReference = new AtomicReference<>();

        ChatModelListener listener = new ChatModelListener() {

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                requestReference.set(requestContext.chatRequest());
                requestContext.attributes().put("id", "12345");
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                responseReference.set(responseContext.chatResponse());
                assertThat(responseContext.chatRequest()).isSameAs(requestReference.get()); // TODO unit test
                assertThat(responseContext.attributes()).containsEntry("id", "12345"); // TODO this should be tested in unit test
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                fail("onError() must not be called. Exception: " + errorContext.error().getMessage());
            }
        };

        ChatLanguageModel model = createModel(listener);

        List<ChatMessage> messages = List.of(UserMessage.from("hello"));

        ToolSpecification toolSpecification = null;
        if (supportsTools()) {
            toolSpecification = ToolSpecification.builder()
                    .name("add")
                    .addParameter("a", INTEGER)
                    .addParameter("b", INTEGER)
                    .build();
        }

        // TODO test for custom params as well
        // TODO test default params and overrides (both common and custom)

        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .temperature(0.666)
                // TODO add more params
                .toolSpecifications(toolSpecification)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(parameters)
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        assertThat(requestReference.get()).isEqualTo(chatRequest);
        assertThat(responseReference.get()).isEqualTo(chatResponse);
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
        AtomicReference<ChatRequest> requestReference = new AtomicReference<>();
        AtomicReference<Throwable> errorReference = new AtomicReference<>();

        ChatModelListener listener = new ChatModelListener() {

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                requestReference.set(requestContext.chatRequest());
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
