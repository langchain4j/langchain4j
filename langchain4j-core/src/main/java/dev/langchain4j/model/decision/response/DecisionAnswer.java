package dev.langchain4j.model.decision.response;

import dev.langchain4j.Experimental;

/**
 * An answer to one question of a {@link dev.langchain4j.model.decision.request.DecisionRequest}.
 * <p>
 * The built-in answer types are {@link YesNoAnswer}, {@link ChoiceAnswer} and {@link ScaleAnswer}, one for each
 * built-in question type. Implementations that support additional question types define their own answer types.
 *
 * @since 1.21.0
 */
@Experimental
public interface DecisionAnswer {}
