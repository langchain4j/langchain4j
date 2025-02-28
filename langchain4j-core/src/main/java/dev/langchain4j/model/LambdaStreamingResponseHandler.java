package dev.langchain4j.model;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

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
            public void onCompleteResponse(ChatResponse completeResponse) {
            }

            @Override
            public void onError(Throwable error) {
                throw new RuntimeException(error);
            }
        };
    }

    public static StreamingChatResponseHandler onPartialResponseAndError(
            Consumer<String> onPartialResponseLambda,
            Consumer<Throwable> onErrorLambda
    ) {
        return new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                onPartialResponseLambda.accept(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
            }

            @Override
            public void onError(Throwable error) {
                onErrorLambda.accept(error);
            }
        };
    }
}
