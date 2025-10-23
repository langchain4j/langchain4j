package dev.langchain4j.model.chat.response;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.concurrent.CompletableFuture;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.junit.jupiter.api.Test;

public class StreamingChatResponseHandlerIT {

    StreamingChatModel model = OpenAiStreamingChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    public void should_receive_partial_responses() throws Exception {

        // given
        StringBuilder responseBuilder = new StringBuilder();
        CompletableFuture<ChatResponse> completableFuture = new CompletableFuture<>();

        StreamingChatResponseHandler handler = spy(new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                responseBuilder.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                completableFuture.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                completableFuture.completeExceptionally(error);
            }
        });

        // when
        model.chat("Hi", handler);
        ChatResponse chatResponse = completableFuture.get(30, SECONDS);

        // then
        assertThat(responseBuilder.toString()).isEqualTo(chatResponse.aiMessage().text());

        verify(handler, atLeastOnce()).onPartialResponse(any(), any()); // LC4j will always call this callback
        verify(handler, atLeastOnce()).onPartialResponse(any());
        verify(handler).onCompleteResponse(any());
        verifyNoMoreInteractions(handler);
    }

    @Test
    public void should_receive_partial_responses_with_context() throws Exception {

        // given
        StringBuilder responseBuilder = new StringBuilder();
        CompletableFuture<ChatResponse> completableFuture = new CompletableFuture<>();

        StreamingChatResponseHandler handler = spy(new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
                responseBuilder.append(partialResponse.text());
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                completableFuture.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                completableFuture.completeExceptionally(error);
            }
        });

        // when
        model.chat("Hi", handler);
        ChatResponse chatResponse = completableFuture.get(30, SECONDS);

        // then
        assertThat(responseBuilder.toString()).isEqualTo(chatResponse.aiMessage().text());

        verify(handler, atLeastOnce()).onPartialResponse(any(), any());
        verify(handler, never()).onPartialResponse(any());
        verify(handler).onCompleteResponse(any());
        verifyNoMoreInteractions(handler);
    }

    @Test
    public void should_receive_partial_responses_only_with_context_when_both_callbacks_are_defined() throws Exception {

        // given
        StringBuilder responseBuilder = new StringBuilder();
        CompletableFuture<ChatResponse> completableFuture = new CompletableFuture<>();

        StreamingChatResponseHandler handler = spy(new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                throw new IllegalStateException("onPartialResponse(String) should never be called " +
                        "if onPartialResponse(PartialResponse, PartialResponseContext) is defined");
            }

            @Override
            public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
                responseBuilder.append(partialResponse.text());
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                completableFuture.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                completableFuture.completeExceptionally(error);
            }
        });

        // when
        model.chat("Hi", handler);
        ChatResponse chatResponse = completableFuture.get(30, SECONDS);

        // then
        assertThat(responseBuilder.toString()).isEqualTo(chatResponse.aiMessage().text());

        verify(handler, atLeastOnce()).onPartialResponse(any(), any());
        verify(handler, never()).onPartialResponse(any());
        verify(handler).onCompleteResponse(any());
        verifyNoMoreInteractions(handler);
    }
}
