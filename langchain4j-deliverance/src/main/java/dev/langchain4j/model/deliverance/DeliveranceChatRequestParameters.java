package dev.langchain4j.model.deliverance;

import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Arrays.asList;

public class DeliveranceChatRequestParameters extends DefaultChatRequestParameters {

    public static final DeliveranceChatRequestParameters EMPTY = builder().build();

    private final Integer ntokens;
    private final Integer seed;
    private final Boolean includeStopStrInOutput;
    private final List<String> guidedChoice;
    private final Boolean logProbs;
    private final Integer topLogProbs;
    private final Double xtcThreshold;
    private final Double xtcProbability;

    protected DeliveranceChatRequestParameters(Builder<?> builder) {
        super(builder);
        this.ntokens = builder.ntokens;
        this.seed = builder.seed;
        this.includeStopStrInOutput = builder.includeStopStrInOutput;
        this.guidedChoice = copy(builder.guidedChoice);
        this.logProbs = builder.logProbs;
        this.topLogProbs = builder.topLogProbs;
        this.xtcThreshold = builder.xtcThreshold;
        this.xtcProbability = builder.xtcProbability;
    }

    public Integer ntokens() {
        return ntokens;
    }

    public Integer seed() {
        return seed;
    }

    public Boolean includeStopStrInOutput() {
        return includeStopStrInOutput;
    }

    public List<String> guidedChoice() {
        return guidedChoice;
    }

    public Boolean logProbs() {
        return logProbs;
    }

    public Integer topLogProbs() {
        return topLogProbs;
    }

    public Double xtcThreshold() {
        return xtcThreshold;
    }

    public Double xtcProbability() {
        return xtcProbability;
    }

    @Override
    public ChatRequestParameters overrideWith(ChatRequestParameters that) {
        return DeliveranceChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public ChatRequestParameters defaultedBy(ChatRequestParameters that) {
        return DeliveranceChatRequestParameters.builder()
                .overrideWith(that)
                .overrideWith(this)
                .build();
    }

    public static Builder<?> builder() {
        return new Builder<>();
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeliveranceChatRequestParameters that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(ntokens, that.ntokens)
                && Objects.equals(seed, that.seed)
                && Objects.equals(includeStopStrInOutput, that.includeStopStrInOutput)
                && Objects.equals(guidedChoice, that.guidedChoice)
                && Objects.equals(logProbs, that.logProbs)
                && Objects.equals(topLogProbs, that.topLogProbs)
                && Objects.equals(xtcThreshold, that.xtcThreshold)
                && Objects.equals(xtcProbability, that.xtcProbability);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                ntokens,
                seed,
                includeStopStrInOutput,
                guidedChoice,
                logProbs,
                topLogProbs,
                xtcThreshold,
                xtcProbability
        );
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "DeliveranceChatRequestParameters{" +
                "super=" + super.toString() +
                ", ntokens=" + ntokens +
                ", seed=" + seed +
                ", includeStopStrInOutput=" + includeStopStrInOutput +
                ", guidedChoice=" + guidedChoice +
                ", logProbs=" + logProbs +
                ", topLogProbs=" + topLogProbs +
                ", xtcThreshold=" + xtcThreshold +
                ", xtcProbability=" + xtcProbability +
                '}';
    }

    public static class Builder<T extends Builder<T>> extends DefaultChatRequestParameters.Builder<T> {

        private Integer ntokens;
        private Integer seed;
        private Boolean includeStopStrInOutput;
        private List<String> guidedChoice;
        private Boolean logProbs;
        private Integer topLogProbs;
        private Double xtcThreshold;
        private Double xtcProbability;

        @Override
        public T overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof DeliveranceChatRequestParameters deliveranceParameters) {
                ntokens(getOrDefault(deliveranceParameters.ntokens(), ntokens));
                seed(getOrDefault(deliveranceParameters.seed(), seed));
                includeStopStrInOutput(getOrDefault(deliveranceParameters.includeStopStrInOutput(), includeStopStrInOutput));
                guidedChoice(getOrDefault(deliveranceParameters.guidedChoice(), guidedChoice));
                logProbs(getOrDefault(deliveranceParameters.logProbs(), logProbs));
                topLogProbs(getOrDefault(deliveranceParameters.topLogProbs(), topLogProbs));
                xtcThreshold(getOrDefault(deliveranceParameters.xtcThreshold(), xtcThreshold));
                xtcProbability(getOrDefault(deliveranceParameters.xtcProbability(), xtcProbability));
            }
            return (T) this;
        }

        public T ntokens(Integer ntokens) {
            this.ntokens = ntokens;
            return (T) this;
        }

        public T seed(Integer seed) {
            this.seed = seed;
            return (T) this;
        }

        public T includeStopStrInOutput(Boolean includeStopStrInOutput) {
            this.includeStopStrInOutput = includeStopStrInOutput;
            return (T) this;
        }

        public T guidedChoice(List<String> guidedChoice) {
            this.guidedChoice = guidedChoice;
            return (T) this;
        }

        public T guidedChoice(String... guidedChoice) {
            return guidedChoice(asList(guidedChoice));
        }

        public T logProbs(Boolean logProbs) {
            this.logProbs = logProbs;
            return (T) this;
        }

        public T topLogProbs(Integer topLogProbs) {
            this.topLogProbs = topLogProbs;
            return (T) this;
        }

        public T xtcThreshold(Double xtcThreshold) {
            this.xtcThreshold = xtcThreshold;
            return (T) this;
        }

        public T xtcProbability(Double xtcProbability) {
            this.xtcProbability = xtcProbability;
            return (T) this;
        }

        public DeliveranceChatRequestParameters build() {
            return new DeliveranceChatRequestParameters(this);
        }
    }
}
