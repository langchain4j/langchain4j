package dev.langchain4j.model.structureddecision;

import dev.langchain4j.Experimental;

/** A typed question evaluated by a {@link StructuredDecisionModel}. */
@Experimental
public interface Question {

    String instructions();
}
