package dev.langchain4j.model.decision;

import dev.langchain4j.Experimental;

/** A typed question evaluated by a {@link DecisionModel}. */
@Experimental
public interface Question {

    String instructions();
}
