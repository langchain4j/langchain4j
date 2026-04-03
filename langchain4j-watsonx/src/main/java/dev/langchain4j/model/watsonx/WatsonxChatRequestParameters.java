package dev.langchain4j.model.watsonx;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import com.ibm.watsonx.ai.chat.model.Thinking;
import com.ibm.watsonx.ai.chat.model.ThinkingEffort;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

public class WatsonxChatRequestParameters extends DefaultChatRequestParameters {

    public static final WatsonxChatRequestParameters EMPTY =
            WatsonxChatRequestParameters.builder().build();

    private final String projectId;
    private final String spaceId;
    private final Thinking thinking;
    private final Map<String, Integer> logitBias;
    private final Boolean logprobs;
    private final Integer topLogprobs;
    private final Integer seed;
    private final String toolChoiceName;
    private final Set<String> guidedChoice;
    private final String guidedRegex;
    private final String guidedGrammar;
    private final Double repetitionPenalty;
    private final Double lengthPenalty;
    private final Duration timeout;

    private WatsonxChatRequestParameters(Builder builder) {
        super(builder);
        this.projectId = builder.projectId;
        this.spaceId = builder.spaceId;
        this.logitBias = builder.logitBias;
        this.logprobs = builder.logprobs;
        this.topLogprobs = builder.topLogprobs;
        this.seed = builder.seed;
        this.toolChoiceName = builder.toolChoiceName;
        this.timeout = builder.timeout;
        this.thinking = builder.thinking;
        this.guidedChoice = builder.guidedChoice;
        this.guidedRegex = builder.guidedRegex;
        this.guidedGrammar = builder.guidedGrammar;
        this.repetitionPenalty = builder.repetitionPenalty;
        this.lengthPenalty = builder.lengthPenalty;
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

    public Duration timeout() {
        return timeout;
    }

    public Thinking thinking() {
        return thinking;
    }

    public Set<String> guidedChoice() {
        return guidedChoice;
    }

    public String guidedRegex() {
        return guidedRegex;
    }

    public String guidedGrammar() {
        return guidedGrammar;
    }

    public Double repetitionPenalty() {
        return repetitionPenalty;
    }

    public Double lengthPenalty() {
        return lengthPenalty;
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

    @Override
    public WatsonxChatRequestParameters defaultedBy(ChatRequestParameters that) {
        return WatsonxChatRequestParameters.builder()
                .overrideWith(that)
                .overrideWith(this)
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
        private Duration timeout;
        private Set<String> guidedChoice;
        private String guidedRegex;
        private String guidedGrammar;
        private Double repetitionPenalty;
        private Double lengthPenalty;
        private Thinking thinking;

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
                timeout(getOrDefault(watsonxParameters.timeout(), timeout));
                thinking(getOrDefault(watsonxParameters.thinking(), thinking));
                guidedChoice(getOrDefault(watsonxParameters.guidedChoice(), guidedChoice));
                guidedRegex(getOrDefault(watsonxParameters.guidedRegex(), guidedRegex));
                guidedGrammar(getOrDefault(watsonxParameters.guidedGrammar(), guidedGrammar));
                repetitionPenalty(getOrDefault(watsonxParameters.repetitionPenalty(), repetitionPenalty));
                lengthPenalty(getOrDefault(watsonxParameters.lengthPenalty(), lengthPenalty));
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

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder thinking(boolean enabled) {
            return thinking(Thinking.builder().enabled(enabled).build());
        }

        public Builder thinking(ExtractionTags tags) {
            if (nonNull(tags)) return thinking(Thinking.of(tags));

            this.thinking = null;
            return this;
        }

        public Builder thinking(ThinkingEffort thinkingEffort) {
            if (nonNull(thinkingEffort)) return thinking(Thinking.of(thinkingEffort));

            this.thinking = null;
            return this;
        }

        public Builder thinking(Thinking thinking) {
            this.thinking = thinking;
            return this;
        }

        public Builder guidedChoice(String... guidedChoice) {
            return guidedChoice(Set.of(guidedChoice));
        }

        public Builder guidedChoice(Set<String> guidedChoices) {
            this.guidedChoice = guidedChoices;
            return this;
        }

        public Builder guidedRegex(String guidedRegex) {
            this.guidedRegex = guidedRegex;
            return this;
        }

        public Builder guidedGrammar(String guidedGrammar) {
            this.guidedGrammar = guidedGrammar;
            return this;
        }

        public Builder repetitionPenalty(Double repetitionPenalty) {
            this.repetitionPenalty = repetitionPenalty;
            return this;
        }

        public Builder lengthPenalty(Double lengthPenalty) {
            this.lengthPenalty = lengthPenalty;
            return this;
        }

        @Override
        public WatsonxChatRequestParameters build() {
            return new WatsonxChatRequestParameters(this);
        }
    }
}
