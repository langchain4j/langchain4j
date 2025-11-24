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

        // when
//        record StreamingResult(
//                SuccessfulHttpResponse response, List<ServerSentEvent> events, Set<Thread> threads) {}
//
//        CompletableFuture<StreamingResult> completableFuture = new CompletableFuture<>();
//
//        ServerSentEventListener listener = new ServerSentEventListener() {
//
//            private final AtomicReference<SuccessfulHttpResponse> response = new AtomicReference<>();
//            private final List<ServerSentEvent> events = new ArrayList<>();
//            private final Set<Thread> threads = new HashSet<>();
//
//            @Override
//            public void onOpen(SuccessfulHttpResponse successfulHttpResponse) {
//                threads.add(Thread.currentThread());
//                response.set(successfulHttpResponse);
//            }
//
//            @Override
//            public void onEvent(ServerSentEvent event) {
//                threads.add(Thread.currentThread());
//                events.add(event);
//            }
//
//            @Override
//            public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
//                threads.add(Thread.currentThread());
//                events.add(event);
//            }
//
//            @Override
//            public void onError(Throwable throwable) {
//                threads.add(Thread.currentThread());
//                completableFuture.completeExceptionally(throwable);
//            }
//
//            @Override
//            public void onClose() {
//                threads.add(Thread.currentThread());
//                completableFuture.complete(new StreamingResult(response.get(), events, threads));
//            }
//        };
//        ServerSentEventListener spyListener = spy(listener);


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
            Thread.sleep(3_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

//        // then
//        StreamingResult streamingResult = completableFuture.get(30, TimeUnit.SECONDS);
//
//        assertThat(streamingResult.response()).isNotNull();
//        assertThat(streamingResult.response().statusCode()).isEqualTo(200);
//        assertThat(streamingResult.response().headers()).isNotEmpty();
//        assertThat(streamingResult.response().body()).isNull();
//
//        assertThat(streamingResult.events()).isNotEmpty();
//        assertThat(streamingResult.events().stream()
//                .map(ServerSentEvent::data)
//                .collect(joining("")))
//                .contains("Berlin");
//
//        assertThat(streamingResult.threads()).hasSize(1);
//        assertThat(streamingResult.threads().iterator().next()).isNotEqualTo(Thread.currentThread());
//
//        InOrder inOrder = inOrder(spyListener);
//        inOrder.verify(spyListener, times(1)).onOpen(any());
//        inOrder.verify(spyListener, atLeastOnce()).onEvent(any(), any());
//        inOrder.verify(spyListener, times(1)).onClose();
//        inOrder.verifyNoMoreInteractions();
//        verifyNoMoreInteractions(spyListener);
    }
}
