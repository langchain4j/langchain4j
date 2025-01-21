package dev.langchain4j.http.streaming;

import dev.langchain4j.Experimental;

@Experimental
public record ServerSentEvent(String event, String data) {
}
