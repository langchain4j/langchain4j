package dev.langchain4j.model.structureddecision;

import dev.langchain4j.Experimental;

/** A typed question evaluated by a {@link StructuredDecisionModel}. */
@Experimental
public sealed interface Question permits NoulQuestion, ChoiceQuestion, ScoreQuestion {

    String instructions();
}
