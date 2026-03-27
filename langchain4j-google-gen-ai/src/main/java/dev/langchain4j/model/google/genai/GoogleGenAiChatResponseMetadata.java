package dev.langchain4j.model.google.genai;

import com.google.genai.types.GenerateContentResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.util.Objects;

/**
 * Google GenAI-specific metadata for {@link dev.langchain4j.model.chat.response.ChatResponse}.
 * Provides access to the raw {@link GenerateContentResponse} from the GenAI Java SDK.
 */
public class GoogleGenAiChatResponseMetadata extends ChatResponseMetadata {

    private final GenerateContentResponse rawResponse;

    private GoogleGenAiChatResponseMetadata(Builder builder) {
        super(builder);
        this.rawResponse = builder.rawResponse;
    }

    /**
     * Returns the raw {@link GenerateContentResponse} from the Google GenAI Java SDK.
     */
    public GenerateContentResponse rawResponse() {
        return rawResponse;
    }

    @Override
    public Builder toBuilder() {
        return ((Builder) super.toBuilder(builder()))
                .rawResponse(rawResponse);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GoogleGenAiChatResponseMetadata that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(rawResponse, that.rawResponse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), rawResponse);
    }

    @Override
    public String toString() {
        return "GoogleGenAiChatResponseMetadata{" + "id='"
                + id() + '\'' + ", modelName='"
                + modelName() + '\'' + ", tokenUsage="
                + tokenUsage() + ", finishReason="
                + finishReason() + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private GenerateContentResponse rawResponse;

        public Builder rawResponse(GenerateContentResponse rawResponse) {
            this.rawResponse = rawResponse;
            return this;
        }

        @Override
        public GoogleGenAiChatResponseMetadata build() {
            return new GoogleGenAiChatResponseMetadata(this);
        }
    }
}
