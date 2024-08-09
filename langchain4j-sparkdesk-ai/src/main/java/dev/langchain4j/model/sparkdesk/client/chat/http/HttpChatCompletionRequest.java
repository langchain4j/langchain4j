package dev.langchain4j.model.sparkdesk.client.chat.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.model.sparkdesk.client.chat.ChatCompletionModel;
import dev.langchain4j.model.sparkdesk.client.message.AssistantMessage;
import dev.langchain4j.model.sparkdesk.client.message.Message;
import dev.langchain4j.model.sparkdesk.client.message.SystemMessage;
import dev.langchain4j.model.sparkdesk.client.message.UserMessage;
import dev.langchain4j.model.sparkdesk.shared.Request;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.model.sparkdesk.client.chat.ChatCompletionModel.SPARK_ULTRA;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;


@Data
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class HttpChatCompletionRequest implements Request {
    private String model;
    private Boolean stream;
    private Float temperature;
    private Integer topK;
    private Integer maxTokens;
    private List<Message> messages;

    private HttpChatCompletionRequest(Builder builder) {
        this.model = builder.model;
        this.temperature = builder.temperature;
        this.topK = builder.topK;
        this.stream = builder.stream;
        this.maxTokens = builder.maxTokens;
        this.messages = builder.messages;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String model = SPARK_ULTRA.toString();
        private Boolean stream;
        private Float temperature;
        private Integer topK;
        private Integer maxTokens;
        private List<Message> messages;

        private Builder() {
        }

        public Builder from(HttpChatCompletionRequest instance) {
            model(instance.model);
            temperature(instance.temperature);
            topK(instance.topK);
            stream(instance.stream);
            maxTokens(instance.maxTokens);
            messages(instance.messages);
            return this;
        }

        public Builder model(ChatCompletionModel model) {
            return model(model.toString());
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder messages(List<Message> messages) {
            if (messages != null) {
                this.messages = unmodifiableList(messages);
            }
            return this;
        }

        public Builder messages(Message... messages) {
            return messages(asList(messages));
        }

        public Builder addSystemMessage(String systemMessage) {
            if (this.messages == null) {
                this.messages = new ArrayList<>();
            }
            this.messages.add(SystemMessage.from(systemMessage));
            return this;
        }

        public Builder addUserMessage(String userMessage) {
            if (this.messages == null) {
                this.messages = new ArrayList<>();
            }
            this.messages.add(UserMessage.from(userMessage));
            return this;
        }

        public Builder addAssistantMessage(String assistantMessage) {
            if (this.messages == null) {
                this.messages = new ArrayList<>();
            }
            this.messages.add(AssistantMessage.from(assistantMessage));
            return this;
        }


        public Builder temperature(Float temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }


        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }


        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public HttpChatCompletionRequest build() {
            return new HttpChatCompletionRequest(this);
        }
    }
}
