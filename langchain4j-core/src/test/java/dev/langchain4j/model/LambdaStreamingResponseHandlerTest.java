package dev.langchain4j.model;

import static dev.langchain4j.model.LambdaStreamingResponseHandler.onPartialResponse;
import static dev.langchain4j.model.LambdaStreamingResponseHandler.onPartialResponseAndError;
import static dev.langchain4j.model.LambdaStreamingResponseHandler.onPartialResponseAndErrorBlocking;
import static dev.langchain4j.model.LambdaStreamingResponseHandler.onPartialResponseBlocking;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class LambdaStreamingResponseHandlerTest implements WithAssertions {

    @Test
    void testOnPartialResponse() {
        // given
        List<Object> tokens = new ArrayList<>();
        tokens.add("The sky ");
        tokens.add("is blue because of ");
        tokens.add("a phenomenon called ");
        tokens.add("Rayleigh scattering.");

        StreamingChatModel model = new DummyModel(tokens);

        // when
        List<Object> receivedTokens = new ArrayList<>();
        model.chat("Why is the sky blue?", onPartialResponse(receivedTokens::add));

        // then
        assertThat(receivedTokens).containsSequence(tokens);
    }

    @Test
    void testOnPartialResponseAndError() {
        // given
        List<Object> tokens = new ArrayList<>();
        tokens.add("Three ");
        tokens.add("Two ");
        tokens.add("One ");
        tokens.add(new RuntimeException("BOOM"));

        StreamingChatModel model = new DummyModel(tokens);

        // when
        List<Object> receivedTokens = new ArrayList<>();
        final Throwable[] thrown = {null};

        model.chat("Create a countdown", onPartialResponseAndError(receivedTokens::add, t -> thrown[0] = t));

        // then
        assertThat(tokens).containsSubsequence(receivedTokens);
        assertThat(thrown[0]).isNotNull();
        assertThat(thrown[0]).isInstanceOf(RuntimeException.class);
        assertThat((thrown[0]).getMessage()).isEqualTo("BOOM");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testOnPartialResponseBlocking() throws InterruptedException {
        // given
        List<Object> tokens = new ArrayList<>();
        tokens.add("Hello ");
        tokens.add("streaming ");
        tokens.add("world!");

        StreamingChatModel model = new AsyncDummyModel(tokens);

        // when
        List<Object> receivedTokens = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        onPartialResponseBlocking(model, "Test message", token -> {
            receivedTokens.add(token);
            // Mark as completed when we receive the last token
            if ("world!".equals(token)) {
                completed.set(true);
            }
        });

        // then
        assertThat(receivedTokens).containsSequence(tokens);
        assertThat(completed.get()).isTrue(); // Should only reach here after completion
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testOnPartialResponseAndErrorBlocking() throws InterruptedException {
        // given
        List<Object> tokens = new ArrayList<>();
        tokens.add("Processing ");
        tokens.add("request ");
        tokens.add("successfully");

        StreamingChatModel model = new AsyncDummyModel(tokens);

        // when
        List<Object> receivedTokens = new ArrayList<>();
        final Throwable[] thrown = {null};
        AtomicBoolean completed = new AtomicBoolean(false);

        onPartialResponseAndErrorBlocking(
                model,
                "Test message",
                token -> {
                    receivedTokens.add(token);
                    if ("successfully".equals(token)) {
                        completed.set(true);
                    }
                },
                t -> thrown[0] = t);

        // then
        assertThat(receivedTokens).containsSequence(tokens);
        assertThat(thrown[0]).isNull(); // No error expected
        assertThat(completed.get()).isTrue();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testOnPartialResponseBlockingWithError() throws InterruptedException {
        // given
        List<Object> tokens = new ArrayList<>();
        tokens.add("Never ");
        tokens.add("ending ");
        tokens.add(new RuntimeException("Something went wrong"));

        StreamingChatModel model = new AsyncDummyModel(tokens);

        // when
        List<Object> receivedTokens = new ArrayList<>();
        AtomicBoolean errorHandled = new AtomicBoolean(false);

        onPartialResponseBlocking(model, "Test message", token -> {
            receivedTokens.add(token);
            // This should be called before the method returns
            if (receivedTokens.size() == 2) {
                errorHandled.set(true);
            }
        });

        // then
        assertThat(receivedTokens).containsExactly("Never ", "ending ");
        assertThat(errorHandled.get()).isTrue(); // Should complete even with error
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testOnPartialResponseAndErrorBlockingWithError() throws InterruptedException {
        // given
        List<Object> tokens = new ArrayList<>();
        tokens.add("Never ");
        tokens.add("ending ");
        tokens.add(new RuntimeException("Something went wrong"));

        StreamingChatModel model = new AsyncDummyModel(tokens);

        // when
        List<Object> receivedTokens = new ArrayList<>();
        final Throwable[] thrown = {null};
        AtomicBoolean completed = new AtomicBoolean(false);

        onPartialResponseAndErrorBlocking(model, "Test message", receivedTokens::add, t -> {
            thrown[0] = t;
            completed.set(true);
        });

        // then
        assertThat(receivedTokens).containsExactly("Never ", "ending ");
        assertThat(thrown[0]).isNotNull();
        assertThat(thrown[0]).isInstanceOf(RuntimeException.class);
        assertThat(thrown[0].getMessage()).isEqualTo("Something went wrong");
        assertThat(completed.get()).isTrue();
    }

    @Test
    void testOnPartialResponseBlockingWithInterruption() {
        // given
        List<Object> tokens = new ArrayList<>();
        // No completion signal - this will hang without interruption
        tokens.add("Never ");
        tokens.add("ending ");

        StreamingChatModel model = new NonCompletingAsyncDummyModel(tokens);

        // when/then
        Thread testThread = Thread.currentThread();

        // Start interruption in another thread after a short delay
        new Thread(() -> {
                    try {
                        Thread.sleep(100);
                        testThread.interrupt();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                })
                .start();

        assertThatThrownBy(() -> onPartialResponseBlocking(model, "Test message", System.out::print))
                .isInstanceOf(InterruptedException.class);
    }

    static class DummyModel implements StreamingChatModel {

        private final List<Object> stringsAndError;

        public DummyModel(List<Object> stringsAndError) {
            this.stringsAndError = stringsAndError;
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            stringsAndError.forEach(obj -> {
                if (obj instanceof String message) {
                    handler.onPartialResponse(message);
                } else if (obj instanceof Throwable problem) {
                    handler.onError(problem);
                }
            });
            // Always call onCompleteResponse at the end if no error occurred
            if (stringsAndError.stream().noneMatch(obj -> obj instanceof Throwable)) {
                handler.onCompleteResponse(null); // Mock ChatResponse
            }
        }
    }

    static class AsyncDummyModel implements StreamingChatModel {

        private final List<Object> stringsAndError;

        public AsyncDummyModel(List<Object> stringsAndError) {
            this.stringsAndError = stringsAndError;
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            // Simulate async behavior with a separate thread
            new Thread(() -> {
                        try {
                            for (Object obj : stringsAndError) {
                                Thread.sleep(50); // Simulate network delay
                                if (obj instanceof String message) {
                                    handler.onPartialResponse(message);
                                } else if (obj instanceof Throwable problem) {
                                    handler.onError(problem);
                                    return; // Exit on error
                                }
                            }
                            // Call onCompleteResponse if no error occurred
                            if (stringsAndError.stream().noneMatch(obj -> obj instanceof Throwable)) {
                                handler.onCompleteResponse(null); // Mock ChatResponse
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            handler.onError(e);
                        }
                    })
                    .start();
        }
    }

    static class NonCompletingAsyncDummyModel implements StreamingChatModel {

        private final List<Object> stringsAndError;

        public NonCompletingAsyncDummyModel(List<Object> stringsAndError) {
            this.stringsAndError = stringsAndError;
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            // Simulate async behavior that never completes
            new Thread(() -> {
                        try {
                            for (Object obj : stringsAndError) {
                                Thread.sleep(50);
                                if (obj instanceof String message) {
                                    handler.onPartialResponse(message);
                                }
                            }
                            // Never call onCompleteResponse - this simulates a hanging connection
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            handler.onError(e);
                        }
                    })
                    .start();
        }
    }
}
