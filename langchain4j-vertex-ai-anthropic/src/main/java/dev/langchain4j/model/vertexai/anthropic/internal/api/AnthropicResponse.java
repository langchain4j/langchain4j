package dev.langchain4j.model.vertexai.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.StringJoiner;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicResponse {

    public String id;
    public String type;
    public String role;
    public String model;
    public List<AnthropicContent> content;
    public String stopReason;
    public String stopSequence;
    public AnthropicUsage usage;

    public AnthropicResponse() {}

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getRole() {
        return role;
    }

    public String getModel() {
        return model;
    }

    public List<AnthropicContent> getContent() {
        return content;
    }

    public String getStopReason() {
        return stopReason;
    }

    public String getStopSequence() {
        return stopSequence;
    }

    public AnthropicUsage getUsage() {
        return usage;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "AnthropicResponse [", "]")
                .add("id=" + this.getId())
                .add("type=" + this.getType())
                .add("role=" + this.getRole())
                .add("model=" + this.getModel())
                .add("content="
                        + (this.getContent() == null ? 0 : this.getContent().size()))
                .add("stopReason=" + this.getStopReason())
                .add("stopSequence=" + this.getStopSequence())
                .add("usage=" + this.getUsage())
                .toString();
    }
}
