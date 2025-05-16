package dev.langchain4j.model.chat.common;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.TestInstance;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

/**
 * Contains all the common tests that every {@link StreamingChatModel} must successfully pass.
 * This ensures that {@link StreamingChatModel} implementations are interchangeable among themselves.
 */
@TestInstance(PER_CLASS)
public abstract class AbstractStreamingChatModelIT extends AbstractBaseChatModelIT<StreamingChatModel> {

    @Override
    protected ChatResponseAndStreamingMetadata chat(StreamingChatModel chatModel, ChatRequest chatRequest) {

        CompletableFuture<ChatResponse> futureChatResponse = new CompletableFuture<>();
        StringBuffer concatenatedPartialResponsesBuilder = new StringBuffer();
        AtomicInteger timesOnPartialResponseWasCalled = new AtomicInteger();
        AtomicInteger timesOnCompleteResponseWasCalled = new AtomicInteger();
        Set<Thread> threads = new CopyOnWriteArraySet<>();

        chatModel.chat(chatRequest, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                concatenatedPartialResponsesBuilder.append(partialResponse);
                timesOnPartialResponseWasCalled.incrementAndGet();
                threads.add(Thread.currentThread());
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureChatResponse.complete(completeResponse);
                timesOnCompleteResponseWasCalled.incrementAndGet();
                threads.add(Thread.currentThread());
            }

            @Override
            public void onError(Throwable error) {
                futureChatResponse.completeExceptionally(error);
                threads.add(Thread.currentThread());
            }
        });

        try {
            ChatResponse chatResponse = futureChatResponse.get(120, SECONDS);
            String concatenatedPartialResponses = concatenatedPartialResponsesBuilder.toString();
            StreamingMetadata metadata = new StreamingMetadata(
                    concatenatedPartialResponses.isEmpty() ? null : concatenatedPartialResponses,
                    timesOnPartialResponseWasCalled.get(),
                    timesOnCompleteResponseWasCalled.get(),
                    threads
            );
            return new ChatResponseAndStreamingMetadata(chatResponse, metadata);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
