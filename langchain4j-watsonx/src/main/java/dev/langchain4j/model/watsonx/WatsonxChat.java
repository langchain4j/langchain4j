package dev.langchain4j.model.watsonx;

import static dev.langchain4j.internal.Utils.copy;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.ibm.watsonx.ai.chat.ChatProvider;
import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import dev.langchain4j.Internal;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.List;

@Internal
abstract class WatsonxChat {

    protected final ChatProvider chatProvider;
    protected final List<ChatModelListener> listeners;
    protected final ChatRequestParameters defaultRequestParameters;
    protected final boolean enableJsonSchema;
    protected final ExtractionTags tags;

    protected WatsonxChat(Builder<?> builder) {
        this.chatProvider = requireNonNull(builder.chatProvider);
        this.listeners = copy(builder.listeners);
        this.defaultRequestParameters = requireNonNullElse(
                builder.defaultRequestParameters,
                WatsonxChatRequestParameters.builder().build());
        this.enableJsonSchema = requireNonNullElse(builder.enableJsonSchema, false);
        this.tags = builder.tags;
    }

    @SuppressWarnings("unchecked")
    protected abstract static class Builder<T extends Builder<T>> {
        private ChatProvider chatProvider;
        private List<ChatModelListener> listeners;
        private ChatRequestParameters defaultRequestParameters;
        private Boolean enableJsonSchema;
        private ExtractionTags tags;

        public T service(ChatProvider chatProvider) {
            this.chatProvider = chatProvider;
            return (T) this;
        }

        public T listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return (T) this;
        }

        public T defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return (T) this;
        }

        public T enableJsonSchema(boolean enableJsonSchema) {
            this.enableJsonSchema = enableJsonSchema;
            return (T) this;
        }

        /**
         * Sets the tag names used to extract segmented content from the assistant's response.
         * <p>
         * The provided {@link ExtractionTags} define which XML-like tags (such as {@code <think>} and {@code <response>}) will be used to extract the
         * response from the {@link AiMessage}.
         * <p>
         * If the {@code response} tag is not specified in {@link ExtractionTags}, it will automatically default to {@code "root"}, meaning that only
         * the text nodes directly under the root element will be treated as the final response.
         * <p>
         * Example:
         *
         * <pre>{@code
         * // Explicitly set both tags
         * builder.thinking(ExtractionTags.of("think", "response")).build();
         *
         * // Only set reasoning tag — response defaults to "root"
         * builder.thinking(ExtractionTags.of("think")).build();
         * }</pre>
         *
         * @param tags an {@link ExtractionTags} instance containing the reasoning and (optionally) response tag names
         */
        public T thinking(ExtractionTags tags) {
            this.tags = tags;
            return (T) this;
        }
    }
}
