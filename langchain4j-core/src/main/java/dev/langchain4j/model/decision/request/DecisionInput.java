package dev.langchain4j.model.decision.request;

import dev.langchain4j.Experimental;

/**
 * The input of a {@link DecisionRequest}: what the questions are answered about.
 * <p>
 * LangChain4j provides one implementation per {@link DecisionInputType}: {@link TextInput}, {@link FieldsInput},
 * {@link ContentsInput} and {@link MessagesInput}. Decision models support only these implementations: they throw an
 * {@link dev.langchain4j.exception.UnsupportedFeatureException} for an input they do not support, including other
 * implementations of this interface. More types can be added in future releases.
 *
 * @since 1.23.0
 */
@Experimental
public interface DecisionInput {

    /**
     * The type of this input.
     */
    DecisionInputType type();
}
