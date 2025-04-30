package dev.langchain4j.langfuse.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Generation {

    @JsonProperty("id")
    private final String id;

    @JsonProperty("name")
    private final String name;

    @JsonProperty("model")
    private final String model;

    @JsonProperty("start_time")
    private final Long startTime;

    @JsonProperty("end_time")
    private Long endTime;

    @JsonProperty("prompt")
    private final String prompt;

    @JsonProperty("completion")
    private final String completion;

    @JsonProperty("prompt_tokens")
    private final Integer promptTokens;

    @JsonProperty("completion_tokens")
    private final Integer completionTokens;

    @JsonProperty("metadata")
    private final Map<String, Object> metadata;

    @JsonProperty("status")
    private String status;

    private Generation(Builder builder) {
        this.id = UUID.randomUUID().toString();
        this.name = builder.name;
        this.model = builder.model;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.prompt = builder.prompt;
        this.completion = builder.completion;
        this.promptTokens = builder.promptTokens;
        this.completionTokens = builder.completionTokens;
        this.metadata = new HashMap<>(builder.metadata);
        this.status = "success";
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getModel() {
        return model;
    }

    public Long getStartTime() {
        return startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getCompletion() {
        return completion;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public static class Builder {
        private String name;
        private String model;
        private Long startTime = System.currentTimeMillis();
        private Long endTime;
        private String prompt;
        private String completion;
        private Integer promptTokens;
        private Integer completionTokens;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder startTime(Long startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Long endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder completion(String completion) {
            this.completion = completion;
            this.endTime = System.currentTimeMillis(); // Set endTime when completion is provided
            return this;
        }

        public Builder promptTokens(Integer promptTokens) {
            this.promptTokens = promptTokens;
            return this;
        }

        public Builder completionTokens(Integer completionTokens) {
            this.completionTokens = completionTokens;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? metadata : new HashMap<>();
            return this;
        }

        public Generation build() {
            if (model == null) {
                throw new IllegalStateException("Model is required");
            }
            if (prompt == null) {
                throw new IllegalStateException("Prompt is required");
            }
            return new Generation(this);
        }
    }
}
