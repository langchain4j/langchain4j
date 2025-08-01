package dev.langchain4j.model.watsonx;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.ibm.watsonx.ai.chat.ChatProvider;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import java.util.List;

abstract class WatsonxChat {

    protected final ChatProvider chatProvider;
    protected final List<ChatModelListener> listeners;
    protected final WatsonxChatRequestParameters defaultRequestParameters;
    protected final Boolean enableJsonSchema;

    protected WatsonxChat(Builder<?> builder) {
        this.chatProvider = requireNonNull(builder.chatProvider);
        this.listeners = requireNonNullElse(builder.listeners, List.of());
        this.defaultRequestParameters = requireNonNullElse(
                builder.defaultRequestParameters,
                WatsonxChatRequestParameters.builder().build());
        this.enableJsonSchema = requireNonNullElse(builder.enableJsonSchema, false);
    }

    @SuppressWarnings("unchecked")
    public abstract static class Builder<T extends Builder<T>> {
        private ChatProvider chatProvider;
        private List<ChatModelListener> listeners;
        private WatsonxChatRequestParameters defaultRequestParameters;
        private Boolean enableJsonSchema;

        public T service(ChatProvider chatProvider) {
            this.chatProvider = chatProvider;
            return (T) this;
        }

        public T listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return (T) this;
        }

        public T defaultRequestParameters(WatsonxChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return (T) this;
        }

        public T enableJsonSchema(boolean enableJsonSchema) {
            this.enableJsonSchema = enableJsonSchema;
            return (T) this;
        }
    }
}
