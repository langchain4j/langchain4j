package dev.langchain4j.model.zhipu.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.model.zhipu.shared.Usage;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ChatCompletionResponse {
    private String id;
    private Integer created;
    private String model;
    private List<ChatCompletionChoice> choices;
    private Usage usage;

    public ChatCompletionResponse(String id, Integer created, String model, List<ChatCompletionChoice> choices, Usage usage) {
        this.id = id;
        this.created = created;
        this.model = model;
        this.choices = choices;
        this.usage = usage;
    }

    public static ChatCompletionResponseBuilder builder() {
        return new ChatCompletionResponseBuilder();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getCreated() {
        return created;
    }

    public void setCreated(Integer created) {
        this.created = created;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<ChatCompletionChoice> getChoices() {
        return choices;
    }

    public void setChoices(List<ChatCompletionChoice> choices) {
        this.choices = choices;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    public static class ChatCompletionResponseBuilder {
        private String id;
        private Integer created;
        private String model;
        private List<ChatCompletionChoice> choices;
        private Usage usage;

        public ChatCompletionResponseBuilder id(String id) {
            this.id = id;
            return this;
        }

        public ChatCompletionResponseBuilder created(Integer created) {
            this.created = created;
            return this;
        }

        public ChatCompletionResponseBuilder model(String model) {
            this.model = model;
            return this;
        }

        public ChatCompletionResponseBuilder choices(List<ChatCompletionChoice> choices) {
            this.choices = choices;
            return this;
        }

        public ChatCompletionResponseBuilder usage(Usage usage) {
            this.usage = usage;
            return this;
        }

        public ChatCompletionResponse build() {
            return new ChatCompletionResponse(this.id, this.created, this.model, this.choices, this.usage);
        }
    }
}
