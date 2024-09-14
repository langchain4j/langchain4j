package dev.langchain4j.model.sparkdesk.shared;


public interface DefaultRequest extends Request {
    RequestHeader getHeader();

    Parameter getParameter();

    RequestPayload getPayload();
}
