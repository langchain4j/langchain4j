package dev.langchain4j.model.zhipu.chat;

import dev.langchain4j.model.zhipu.shared.Usage;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    public String getId() {
        return id;
    }

    public Integer getCreated() {
        return created;
    }

    public String getModel() {
        return model;
    }

    public List<ChatCompletionChoice> getChoices() {
        return choices;
    }

    public Usage getUsage() {
        return usage;
    }

    public String content() {
        return ((AssistantMessage) this.getChoices().get(0).getMessage()).getContent();
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) {
            return true;
        } else {
            return another instanceof ChatCompletionResponse && this.equalTo((ChatCompletionResponse) another);
        }
    }

    private boolean equalTo(ChatCompletionResponse another) {
        return Objects.equals(this.id, another.id)
                && Objects.equals(this.created, another.created)
                && Objects.equals(this.model, another.model)
                && Objects.equals(this.choices, another.choices)
                && Objects.equals(this.usage, another.usage);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(this.id);
        h += (h << 5) + Objects.hashCode(this.created);
        h += (h << 5) + Objects.hashCode(this.model);
        h += (h << 5) + Objects.hashCode(this.choices);
        h += (h << 5) + Objects.hashCode(this.usage);
        return h;
    }

    @Override
    public String toString() {
        return "ChatCompletionResponse{" +
                "id=" + this.id
                + ", created=" + this.created
                + ", model=" + this.model
                + ", choices=" + this.choices
                + ", usage=" + this.usage
                + "}";
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
            if (choices != null) {
                this.choices = Collections.unmodifiableList(choices);
            }

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
