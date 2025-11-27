package dev.langchain4j.http.client.jdk;

import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.StreamingHttpEvent;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;

import java.net.http.HttpClient;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static org.reactivestreams.FlowAdapters.toPublisher;

// TODO move to the core http module?
public class StreamingHttpEventPublisherIT extends PublisherVerification<StreamingHttpEvent> {

    public static final long DEFAULT_TIMEOUT_MILLIS = 2_000L;
    public static final long DEFAULT_NO_SIGNALS_TIMEOUT_MILLIS = DEFAULT_TIMEOUT_MILLIS;
    public static final long DEFAULT_POLL_TIMEOUT_MILLIS = 20L;
    public static final long PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS = 300L;

    public StreamingHttpEventPublisherIT() {
        super(new TestEnvironment(DEFAULT_TIMEOUT_MILLIS, DEFAULT_NO_SIGNALS_TIMEOUT_MILLIS, DEFAULT_POLL_TIMEOUT_MILLIS), PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS);
    }

    @Override
    public Publisher<StreamingHttpEvent> createPublisher(long elements) {

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + System.getenv("OPENAI_API_KEY"))
                .addHeader("Content-Type", "application/json")
                .body(
                        """
                                {
                                    "model": "gpt-4o-mini",
                                    "messages": [
                                        {
                                            "role" : "user",
                                            "content" : "What is the capital of Germany?"
                                        }
                                    ],
                                    "stream": true
                                }
                                """)
                .build();

        JdkHttpClient jdkHttpClient = JdkHttpClient.builder().build();
        java.net.http.HttpRequest jdkHttpRequest = jdkHttpClient.toJdkRequest(httpRequest);

        JdkHttpClient.StreamingHttpEventPublisher publisher =
                new JdkHttpClient.StreamingHttpEventPublisher(HttpClient.newHttpClient(), jdkHttpRequest);

        return toPublisher(publisher);
    }

    @Override
    public Publisher<StreamingHttpEvent> createFailedPublisher() {
        return null;
    }
}
