package dev.langchain4j.http;

import dev.langchain4j.Experimental;

@Experimental
public record ServerSentEvent(String type, String data) {
}
