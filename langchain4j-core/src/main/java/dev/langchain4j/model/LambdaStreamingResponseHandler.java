package dev.langchain4j.model;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Utility class with lambda-based streaming response handlers.
 * <p>
 * Lets you use Java lambda functions to receive <code>onPartialResponse</code> and <code>onError</code> events,
 * from your streaming chat model, instead of creating an anonymous inner class
 * implementing <code>StreamingChatResponseHandler</code>.
 * <p>
 * Example:
 * <pre>
 * import static dev.langchain4j.model.LambdaStreamingResponseHandler.*;
 *
 * model.chat("Why is the sky blue?",
 *       onPartialResponse(text -&gt; System.out.println(text));
 * model.chat("Why is the sky blue?",
 *       onPartialResponse(System.out::println);
 * model.chat("Why is the sky blue?",
 *       onPartialResponseAndError(System.out::println, Throwable::printStackTrace));
 *
 * // Blocking
 * onPartialResponseBlocking(model, "Why is the sky blue?", System.out::print);
 * onPartialResponseAndErrorBlocking(model, "Why is the sky blue?",
 *      System.out::print, Throwable::printStackTrace);
 * </pre>
 *
 * @see StreamingChatResponseHandler#onPartialResponse(String)
 * @see StreamingChatResponseHandler#onError(Throwable)
 */
public class LambdaStreamingResponseHandler {

    public static StreamingChatResponseHandler onPartialResponse(Consumer<String> onPartialResponse) {
        return new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                onPartialResponse.accept(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {}

            @Override
            public void onError(Throwable error) {
                throw new RuntimeException(error);
            }
        };
    }

    public static StreamingChatResponseHandler onPartialResponseAndError(
            Consumer<String> onPartialResponseLambda, Consumer<Throwable> onErrorLambda) {
        return new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                onPartialResponseLambda.accept(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {}

            @Override
            public void onError(Throwable error) {
                onErrorLambda.accept(error);
            }
        };
    }

    /**
     * Creates a streaming response handler that processes partial responses with the given consumer
     * and blocks until the streaming is complete.
     *
     * @param model the streaming chat model to use
     * @param message the message to send
     * @param onPartialResponse consumer to handle each partial response
     * @throws InterruptedException if the thread is interrupted while waiting for completion
     */
    public static void onPartialResponseBlocking(
            StreamingChatModel model, String message, Consumer<String> onPartialResponse) throws InterruptedException {

        onPartialResponseAndErrorBlocking(model, message, onPartialResponse, Throwable::printStackTrace);
    }

    /**
     * Creates a streaming response handler that processes partial responses and errors with the given consumers
     * and blocks until the streaming is complete.
     *
     * @param model the streaming chat model to use
     * @param message the message to send
     * @param onPartialResponse consumer to handle each partial response
     * @param onError consumer to handle errors
     * @throws InterruptedException if the thread is interrupted while waiting for completion
     */
    public static void onPartialResponseAndErrorBlocking(
            StreamingChatModel model, String message, Consumer<String> onPartialResponse, Consumer<Throwable> onError)
            throws InterruptedException {

        CountDownLatch completionLatch = new CountDownLatch(1);

        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                onPartialResponse.accept(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                completionLatch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                onError.accept(error);
                completionLatch.countDown();
            }
        };

        model.chat(message, handler);
        completionLatch.await();
    }
}
