package dev.langchain4j.model;

import java.util.function.Consumer;

/**
 * Utility class with lambda-based streaming response handlers.
 *
 * Lets you use Java lambda functions to receive <code>onNext</code> and <code>onError</code> events,
 * from your streaming chat model, instead of creating an anonymous inner class
 * implementing <code>StreamingResponseHandler</code>.
 *
 * Example:
 * <pre>
 * import static dev.langchain4j.model.LambdaStreamingResponseHandler.*;
 *
 * model.generate("Why is the sky blue?",
 *       onNext(text -&gt; System.out.println(text));
 * model.generate("Why is the sky blue?",
 *       onNext(System.out::println);
 * model.generate("Why is the sky blue?",
 *       onNextAndError(System.out::println, Throwable::printStackTrace));
 * </pre>
 *
 * @param <T> The type of the response.
 *
 * @see StreamingResponseHandler#onNext(String)
 * @see StreamingResponseHandler#onError(Throwable)
 */
public class LambdaStreamingResponseHandler<T> {
    public static <T> StreamingResponseHandler<T> onNext(Consumer<String> nextLambda) {
        return new StreamingResponseHandler<T>() {
            @Override
            public void onNext(String text) {
                nextLambda.accept(text);
            }

            @Override
            public void onError(Throwable error) {
                throw new RuntimeException(error);
            }
        };
    }

    public static <T> StreamingResponseHandler<T> onNextAndError(Consumer<String> nextLambda, Consumer<Throwable> errorLambda) {
        return new StreamingResponseHandler<T>() {
            @Override
            public void onNext(String text) {
                nextLambda.accept(text);
            }

            @Override
            public void onError(Throwable error) {
                errorLambda.accept(error);
            }
        };
    }
}
