package dev.langchain4j.service.decision.internal;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.decision.ThresholdProvider;

/**
 * The configuration of a decision service, shared by all its methods: the service interface, the decision model and
 * the optional {@link ThresholdProvider}. New options are added here, so that the signatures of
 * {@link DecisionMethod#invoke(DecisionServiceConfig, Object[])} and
 * {@link DecisionMethod#invokeAsync(DecisionServiceConfig, Object[])} stay stable for frameworks.
 */
@Internal
public final class DecisionServiceConfig {

    private final Class<?> serviceInterface;
    private final DecisionModel decisionModel;
    private final ThresholdProvider thresholdProvider;

    private DecisionServiceConfig(Builder builder) {
        this.serviceInterface = ensureNotNull(builder.serviceInterface, "serviceInterface");
        this.decisionModel = ensureNotNull(builder.decisionModel, "decisionModel");
        this.thresholdProvider = builder.thresholdProvider;
    }

    public Class<?> serviceInterface() {
        return serviceInterface;
    }

    public DecisionModel decisionModel() {
        return decisionModel;
    }

    /**
     * The threshold provider, or {@code null} if the default threshold of 0.5 applies.
     */
    public ThresholdProvider thresholdProvider() {
        return thresholdProvider;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Class<?> serviceInterface;
        private DecisionModel decisionModel;
        private ThresholdProvider thresholdProvider;

        public Builder serviceInterface(Class<?> serviceInterface) {
            this.serviceInterface = serviceInterface;
            return this;
        }

        public Builder decisionModel(DecisionModel decisionModel) {
            this.decisionModel = decisionModel;
            return this;
        }

        public Builder thresholdProvider(ThresholdProvider thresholdProvider) {
            this.thresholdProvider = thresholdProvider;
            return this;
        }

        public DecisionServiceConfig build() {
            return new DecisionServiceConfig(this);
        }
    }
}
