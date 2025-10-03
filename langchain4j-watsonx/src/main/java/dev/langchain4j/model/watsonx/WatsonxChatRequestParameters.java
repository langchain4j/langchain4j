package dev.langchain4j.model.watsonx;

import static dev.langchain4j.internal.Utils.getOrDefault;

import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.time.Duration;
import java.util.Map;

public class WatsonxChatRequestParameters extends DefaultChatRequestParameters {

    public static final WatsonxChatRequestParameters EMPTY =
            WatsonxChatRequestParameters.builder().build();

    private final String projectId;
    private final String spaceId;
    private final ExtractionTags tags;
    private final Map<String, Integer> logitBias;
    private final Boolean logprobs;
    private final Integer topLogprobs;
    private final Integer seed;
    private final String toolChoiceName;
    private final Duration timeLimit;

    private WatsonxChatRequestParameters(Builder builder) {
        super(builder);
        this.projectId = builder.projectId;
        this.spaceId = builder.spaceId;
        this.logitBias = builder.logitBias;
        this.logprobs = builder.logprobs;
        this.topLogprobs = builder.topLogprobs;
        this.seed = builder.seed;
        this.toolChoiceName = builder.toolChoiceName;
        this.timeLimit = builder.timeLimit;
        this.tags = builder.tags;
    }

    public String projectId() {
        return projectId;
    }

    public String spaceId() {
        return spaceId;
    }

    public Map<String, Integer> logitBias() {
        return logitBias;
    }

    public Boolean logprobs() {
        return logprobs;
    }

    public Integer topLogprobs() {
        return topLogprobs;
    }

    public Integer seed() {
        return seed;
    }

    public String toolChoiceName() {
        return toolChoiceName;
    }

    public Duration timeLimit() {
        return timeLimit;
    }

    public ExtractionTags thinking() {
        return tags;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ChatRequestParameters overrideWith(ChatRequestParameters that) {
        return WatsonxChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {
        private String projectId;
        private String spaceId;
        private Map<String, Integer> logitBias;
        private Boolean logprobs;
        private Integer topLogprobs;
        private Integer seed;
        private String toolChoiceName;
        private Duration timeLimit;
        private ExtractionTags tags;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof WatsonxChatRequestParameters watsonxParameters) {
                projectId(getOrDefault(watsonxParameters.projectId(), projectId));
                spaceId(getOrDefault(watsonxParameters.spaceId(), spaceId));
                logitBias(getOrDefault(watsonxParameters.logitBias(), logitBias));
                logprobs(getOrDefault(watsonxParameters.logprobs(), logprobs));
                topLogprobs(getOrDefault(watsonxParameters.topLogprobs(), topLogprobs));
                seed(getOrDefault(watsonxParameters.seed(), seed));
                toolChoiceName(getOrDefault(watsonxParameters.toolChoiceName(), toolChoiceName));
                timeLimit(getOrDefault(watsonxParameters.timeLimit(), timeLimit));
                thinking(getOrDefault(watsonxParameters.thinking(), tags));
            }
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder spaceId(String spaceId) {
            this.spaceId = spaceId;
            return this;
        }

        public Builder logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return this;
        }

        public Builder logprobs(Boolean logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public Builder topLogprobs(Integer topLogprobs) {
            this.topLogprobs = topLogprobs;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public Builder toolChoiceName(String toolChoiceName) {
            this.toolChoiceName = toolChoiceName;
            return this;
        }

        public Builder timeLimit(Duration timeLimit) {
            this.timeLimit = timeLimit;
            return this;
        }

        /**
         * Sets the tag names used to extract segmented content from the assistant's response.
         * <p>
         * The provided {@link ExtractionTags} define which XML-like tags (such as {@code <think>} and {@code <response>}) will be used to extract the
         * response from the {@link AiMessage}.
         * <p>
         * If the {@code response} tag is not specified in {@link ExtractionTags}, it will automatically default to {@code "root"}, meaning that only
         * the text nodes directly under the root element will be treated as the final response.
         * <p>
         * Example:
         *
         * <pre>{@code
         * // Explicitly set both tags
         * builder.thinking(ExtractionTags.of("think", "response")).build();
         *
         * // Only set reasoning tag — response defaults to "root"
         * builder.thinking(ExtractionTags.of("think")).build();
         * }</pre>
         *
         * @param tags an {@link ExtractionTags} instance containing the reasoning and (optionally) response tag names
         */
        public Builder thinking(ExtractionTags tags) {
            this.tags = tags;
            return this;
        }

        public WatsonxChatRequestParameters build() {
            return new WatsonxChatRequestParameters(this);
        }
    }
}
