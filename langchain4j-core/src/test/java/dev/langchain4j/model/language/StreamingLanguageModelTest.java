package dev.langchain4j.model.language;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Response;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class StreamingLanguageModelTest implements WithAssertions {
    public static class EchoStreamingLanguageModel implements StreamingLanguageModel {
        @Override
        public void generate(String prompt, StreamingResponseHandler<String> handler) {
            handler.onComplete(new Response<>(prompt));
        }
    }

    public static class CaptureHandler<T> implements StreamingResponseHandler<T> {
        Response<T> response;

        @Override
        public void onNext(String token) {
            throw new RuntimeException("Not implemented");
        }

        @Override
        public void onComplete(Response<T> response) {
            this.response = response;
        }

        @Override
        public void onError(Throwable error) {
            throw new RuntimeException("Not implemented");
        }
    }

    @Test
    void generate() {
        StreamingLanguageModel model = new EchoStreamingLanguageModel();

        CaptureHandler<String> handler = new CaptureHandler<>();
        model.generate(new Prompt("text"), handler);

        assertThat(handler.response).isEqualTo(new Response<>("text"));
    }
}
