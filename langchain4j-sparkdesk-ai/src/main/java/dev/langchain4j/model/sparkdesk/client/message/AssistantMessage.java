package dev.langchain4j.model.sparkdesk.client.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.model.sparkdesk.client.Role;
import dev.langchain4j.model.sparkdesk.client.chat.wss.function.FunctionCall;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.model.sparkdesk.client.Role.ASSISTANT;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AssistantMessage implements Message {

    private final Role role = ASSISTANT;
    private String content;
    private String contentType;
    private FunctionCall functionCall;

    private AssistantMessage(Builder builder) {
        this.content = builder.content;
        this.functionCall = builder.functionCall;
        this.contentType = builder.contentType;
    }

    public static AssistantMessage from(String content) {
        return AssistantMessage.builder()
                .content(content)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Role getRole() {
        return role;
    }

    public static final class Builder {

        private String content;
        private String contentType;
        private FunctionCall functionCall;

        private Builder() {
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder functionCall(FunctionCall functionCall) {
            this.functionCall = functionCall;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }


        public AssistantMessage build() {
            return new AssistantMessage(this);
        }
    }
}