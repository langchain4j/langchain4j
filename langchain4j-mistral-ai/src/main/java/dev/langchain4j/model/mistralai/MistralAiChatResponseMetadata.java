package dev.langchain4j.model.mistralai;

import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionChoice;
import java.util.List;
import java.util.Objects;

public class MistralAiChatResponseMetadata extends ChatResponseMetadata {

    private String object;
    private Integer created;
    private List<MistralAiChatCompletionChoice> choices;

    private MistralAiChatResponseMetadata(Builder builder) {
        super(builder);
        this.created = builder.created;
        this.object = builder.object;
        this.choices = builder.choices;
    }

    public Integer created() {
        return created;
    }

    public String object() {
        return object;
    }

    public List<MistralAiChatCompletionChoice> choices() {
        return choices;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        MistralAiChatResponseMetadata that = (MistralAiChatResponseMetadata) o;
        return Objects.equals(created, that.created)
                && Objects.equals(object, that.object)
                && Objects.equals(choices, that.choices);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), created, object, choices);
    }

    @Override
    public String toString() {
        return "MistralAiChatResponseMetadata{"
                + "id='" + id() + '\''
                + ", modelName='" + modelName() + '\''
                + ", tokenUsage=" + tokenUsage()
                + ", finishReason=" + finishReason()
                + ", created=" + created
                + ", object='" + object + '\''
                + ", choices='" + choices + '\''
                + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private String object;
        private Integer created;
        private List<MistralAiChatCompletionChoice> choices;

        public Builder created(Integer created) {
            this.created = created;
            return this;
        }

        public Builder object(String object) {
            this.object = object;
            return this;
        }

        public Builder choices(List<MistralAiChatCompletionChoice> choices) {
            this.choices = choices;
            return this;
        }

        @Override
        public MistralAiChatResponseMetadata build() {
            return new MistralAiChatResponseMetadata(this);
        }
    }
}
