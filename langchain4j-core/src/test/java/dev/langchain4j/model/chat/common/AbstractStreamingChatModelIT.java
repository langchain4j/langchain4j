package dev.langchain4j.model.chat.common;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
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
 * This test makes sure that all {@link StreamingChatLanguageModel} implementations behave consistently.
 * <p>
 * Make sure these dependencies are present in the module where this test class is extended:
 * <pre>
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
 * </pre>
 */
@TestInstance(PER_CLASS)
public abstract class AbstractStreamingChatModelIT extends AbstractBaseChatModelIT<StreamingChatLanguageModel> {

    @Override
    protected ModelInvocationResult invoke(StreamingChatLanguageModel chatModel, ChatRequest chatRequest) {

        CompletableFuture<ChatResponse> futureChatResponse = new CompletableFuture<>();
        StringBuffer concatenatedPartialResponses = new StringBuffer();
        AtomicInteger timesOnPartialResponseWasCalled = new AtomicInteger();
        AtomicInteger timesOnCompleteResponseWasCalled = new AtomicInteger();
        Set<Thread> threads = new CopyOnWriteArraySet<>();

        chatModel.chat(chatRequest, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                concatenatedPartialResponses.append(partialResponse);
                timesOnPartialResponseWasCalled.incrementAndGet();
                threads.add(Thread.currentThread());
            }

            @Override
            public void onCompleteResponse(ChatResponse chatResponse) {
                futureChatResponse.complete(chatResponse);
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
            ChatResponse chatResponse = futureChatResponse.get(60, SECONDS);
            StreamingMetadata metadata = new StreamingMetadata(
                    valueOrNullIfEmpty(concatenatedPartialResponses.toString()),
                    timesOnPartialResponseWasCalled.get(),
                    timesOnCompleteResponseWasCalled.get(),
                    threads
            );
            return new ModelInvocationResult(chatResponse, metadata);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String valueOrNullIfEmpty(String string) {
        return string.isEmpty() ? null : string;
    }
}
