package dev.langchain4j.model.decision.request;

import dev.langchain4j.Experimental;

/**
 * A question asked to a {@link dev.langchain4j.model.decision.DecisionModel}.
 * <p>
 * The built-in question types are {@link YesNoQuestion}, {@link ChoiceQuestion} and {@link ScaleQuestion}.
 * Implementations may define additional question types; a model that receives a question type it does not support
 * throws {@link dev.langchain4j.exception.UnsupportedFeatureException}.
 *
 * @since 1.21.0
 */
@Experimental
public interface Question {

    /**
     * What the model should evaluate, for example "Which team should handle this ticket?".
     */
    String text();
}
