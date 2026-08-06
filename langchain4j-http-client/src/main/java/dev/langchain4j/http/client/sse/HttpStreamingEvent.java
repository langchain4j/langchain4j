package dev.langchain4j.http.client.sse;

/**
 * An event emitted while consuming a streaming HTTP response via
 * {@link dev.langchain4j.http.client.HttpClient#stream(dev.langchain4j.http.client.HttpRequest)}. This is the
 * reactive counterpart of {@link ServerSentEventListener}: the publisher emits a {@link HttpResponseReceived}
 * (the response head — status and headers) once, followed by a {@link ServerSentEvent} for each parsed
 * server-sent event.
 * <p>
 * This is an open interface on purpose — additional event kinds may be introduced over time — so consumers
 * must be prepared to receive event types they do not recognize and ignore them.
 */
public interface HttpStreamingEvent {
}
