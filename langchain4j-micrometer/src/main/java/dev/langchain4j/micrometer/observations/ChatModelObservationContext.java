package dev.langchain4j.micrometer.observations;

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

    public static Builder builder() {
        return new Builder();
    }


    public static final class Builder {
        private ChatModelRequestContext requestContext;
        private ChatModelResponseContext responseContext;
        private ChatModelErrorContext errorContext;

        private Builder() {}

        public Builder requestContext(ChatModelRequestContext requestContext) {
            this.requestContext = requestContext;
            return this;
        }

        public Builder responseContext(ChatModelResponseContext responseContext) {
            this.responseContext = responseContext;
            return this;
        }

        public Builder errorContext(ChatModelErrorContext errorContext) {
            this.errorContext = errorContext;
            return this;
        }

        public ChatModelObservationContext build() {
            return new ChatModelObservationContext(requestContext, responseContext, errorContext);
        }
    }
}
