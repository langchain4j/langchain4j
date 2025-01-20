package dev.langchain4j.http;

import dev.langchain4j.Experimental;

@Experimental
public record ServerSentEvent(String event, String data) {
    // TODO id, retry, comment?
    // TODO anticipate that more fields will be added, perhaps create a builder?
}
