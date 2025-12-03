package dev.langchain4j.http.client.jdk;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.StreamingHttpEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscription;

import static dev.langchain4j.http.client.HttpMethod.POST;

public class TestPublisher {

    @Test
    void test() {

        HttpClient client = JdkHttpClient.builder()
                .build();

        HttpRequest request = HttpRequest.builder()
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

        Publisher<StreamingHttpEvent> publisher = client.executeWithPublisher(request);

        publisher.subscribe(new Flow.Subscriber<>() {

            Subscription subscription;

            @Override
            public void onSubscribe(Subscription subscription) {
                this.subscription = subscription;
                System.out.println("OLOLO onSubscribe");
                subscription.request(1);
            }

            @Override
            public void onNext(StreamingHttpEvent item) {
                System.out.println("OLOLO onNext: " + item);
                subscription.request(1);
            }

            @Override
            public void onError(Throwable throwable) {
                System.out.println("OLOLO onError");
                throwable.printStackTrace();
            }

            @Override
            public void onComplete() {
                System.out.println("OLOLO onComplete");
            }
        });

        try {
            Thread.sleep(30_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
