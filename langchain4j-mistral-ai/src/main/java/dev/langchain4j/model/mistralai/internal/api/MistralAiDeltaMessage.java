package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiDeltaMessage {

    private MistralAiRole role;

    @JsonDeserialize(using = MistralAiMessageContentDeserializer.class)
    private List<MistralAiMessageContent> content;

    private List<MistralAiToolCall> toolCalls;

    @JsonCreator
    public MistralAiDeltaMessage(
            @JsonProperty("role") MistralAiRole role,
            @JsonProperty("content") List<MistralAiMessageContent> content,
            @JsonProperty("tool_calls") List<MistralAiToolCall> toolCalls) {
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
    }

    public MistralAiRole getRole() {
        return this.role;
    }

    public List<MistralAiMessageContent> getContent() {
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
}
