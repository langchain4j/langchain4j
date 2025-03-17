package dev.langchain4j.model;

import dev.langchain4j.model.output.Response;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class StreamingResponseHandlerTest implements WithAssertions {
    public static class MinimalStreamingResponseHandler<T> implements StreamingResponseHandler<T> {
        @Override
        public void onNext(String token) {}

        @Override
        public void onError(Throwable error) {}

        // onComplete has a default implementation.
    }

    @Test
    void minimalStreamingResponseHandler() {
        StreamingResponseHandler<String> handler = new MinimalStreamingResponseHandler<>();

        // Verify that the default implementation of onComplete does nothing.
        handler.onComplete(new Response<>("test"));
    }
}
