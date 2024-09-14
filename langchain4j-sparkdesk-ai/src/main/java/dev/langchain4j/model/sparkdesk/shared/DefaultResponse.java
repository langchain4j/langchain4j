package dev.langchain4j.model.sparkdesk.shared;

public interface DefaultResponse extends Response {
    ResponseHeader getHeader();

    ResponsePayload getPayload();
}
