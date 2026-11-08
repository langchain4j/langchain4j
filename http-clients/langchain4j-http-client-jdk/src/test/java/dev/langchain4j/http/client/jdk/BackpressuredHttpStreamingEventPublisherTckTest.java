package dev.langchain4j.http.client.jdk;

import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.concurrent.Flow;

public class BackpressuredHttpStreamingEventPublisherTckTest extends AbstractHttpStreamingEventPublisherTck {

    @Override
    protected Flow.Publisher<HttpStreamingEvent> createHttpPublisher(
            HttpClient client, HttpRequest request, ServerSentEventParser parser) {
        return new BackpressuredHttpStreamingEventPublisher(client, request, parser);
    }
}
