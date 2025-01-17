package dev.langchain4j.micrometer.observation;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.micrometer.observation.Observation;

public class ChatModelObservationContext extends Observation.Context {

    ChatModelRequestContext requestContext;

    ChatModelResponseContext responseContext;

    ChatModelErrorContext errorContext;

    public ChatModelObservationContext(
            ChatModelRequestContext requestContext,
            ChatModelResponseContext responseContext,
            ChatModelErrorContext errorContext) {
        this.requestContext = requestContext;
        this.responseContext = responseContext;
        this.errorContext = errorContext;
    }

    public ChatModelRequestContext getRequestContext() {
        return requestContext;
    }

    public ChatModelResponseContext getResponseContext() {
        return responseContext;
    }

    public void setResponseContext(ChatModelResponseContext responseContext) {
        this.responseContext = responseContext;
    }

    public ChatModelErrorContext getErrorContext() {
        return errorContext;
    }

    public void setErrorContext(ChatModelErrorContext errorContext) {
        this.errorContext = errorContext;
    }
}
