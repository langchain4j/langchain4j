package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
@JsonDeserialize(builder = MistralAiDeltaMessage.MistralAiDeltaMessageBuilder.class)
public class MistralAiDeltaMessage {

    private MistralAiRole role;
    private String content;
    private List<MistralAiToolCall> toolCalls;

    private MistralAiDeltaMessage(MistralAiDeltaMessageBuilder builder) {
        this.role = builder.role;
        this.content = builder.content;
        this.toolCalls = builder.toolCalls;
    }

    public MistralAiRole getRole() {
        return this.role;
    }

    public String getContent() {
        return this.content;
    }

    public List<MistralAiToolCall> getToolCalls() {
        return this.toolCalls;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 23 * hash + Objects.hashCode(this.role);
        hash = 23 * hash + Objects.hashCode(this.content);
        hash = 23 * hash + Objects.hashCode(this.toolCalls);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final MistralAiDeltaMessage other = (MistralAiDeltaMessage) obj;
        return Objects.equals(this.content, other.content)
                && this.role == other.role
                && Objects.equals(this.toolCalls, other.toolCalls);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "MistralAiDeltaMessage [", "]")
                .add("role=" + this.getRole())
                .add("content=" + this.getContent())
                .add("toolCalls=" + this.getToolCalls())
                .toString();
    }

    public static MistralAiDeltaMessageBuilder builder() {
        return new MistralAiDeltaMessageBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class MistralAiDeltaMessageBuilder {
        private MistralAiRole role;
        private String content;
        private List<MistralAiToolCall> toolCalls;

        private MistralAiDeltaMessageBuilder() {}
        /**
         * @return {@code this}.
         */
        public MistralAiDeltaMessageBuilder role(MistralAiRole role) {
            this.role = role;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiDeltaMessageBuilder content(String content) {
            this.content = content;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiDeltaMessageBuilder toolCalls(List<MistralAiToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public MistralAiDeltaMessage build() {
            return new MistralAiDeltaMessage(this);
        }
    }
}
