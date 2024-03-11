package dev.langchain4j.model.language;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Response;
import reactor.core.publisher.Flux;

/**
 * Represents a language model that has a simple text interface (as opposed to a chat interface)
 * and can stream a response one token at a time.
 * It is recommended to use the {@link dev.langchain4j.model.chat.StreamingChatLanguageModel} instead,
 * as it offers more features.
 */
public interface StreamingLanguageModel {

    /**
     * Generates a response from the model based on a prompt.
     *
     * @param prompt  The prompt.
     * @param handler The handler for streaming the response.
     */
    void generate(String prompt, StreamingResponseHandler<String> handler);

    /**
     * Generates a response from the model based on a prompt.
     *
     * @param prompt  The prompt.
     * @param handler The handler for streaming the response.
     */
    default void generate(Prompt prompt, StreamingResponseHandler<String> handler) {
        generate(prompt.text(), handler);
    }

    default Flux<String> generate(Prompt prompt) {
        return Flux.create(sink -> generate(prompt, new StreamingResponseHandler<String>() {
            @Override
            public void onNext(String token) {
                sink.next(token);
            }

            @Override
            public void onComplete(Response<String> response) {
                sink.next(response.content());
                sink.complete();
            }

            @Override
            public void onError(Throwable error) {
                sink.error(error);
            }
        }));
    }
}
