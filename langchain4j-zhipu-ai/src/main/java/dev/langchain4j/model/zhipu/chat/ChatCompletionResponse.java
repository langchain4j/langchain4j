package dev.langchain4j.model.zhipu.chat;

import dev.langchain4j.model.zhipu.shared.Usage;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@ToString
@EqualsAndHashCode
public final class ChatCompletionResponse {
    private final String id;
    private final Integer created;
    private final String model;
    private final List<ChatCompletionChoice> choices;
    private final Usage usage;

    private ChatCompletionResponse(Builder builder) {
        this.id = builder.id;
        this.created = builder.created;
        this.model = builder.model;
        this.choices = builder.choices;
        this.usage = builder.usage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String content() {
        return getChoices().get(0).getMessage().getContent();
    }

    public static final class Builder {
        private String id;
        private Integer created;
        private String model;
        private List<ChatCompletionChoice> choices;
        private Usage usage;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder created(Integer created) {
            this.created = created;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder choices(List<ChatCompletionChoice> choices) {
            this.choices = choices;
            return this;
        }

        public Builder usage(Usage usage) {
            this.usage = usage;
            return this;
        }

        public ChatCompletionResponse build() {
            return new ChatCompletionResponse(this);
        }
    }
}
