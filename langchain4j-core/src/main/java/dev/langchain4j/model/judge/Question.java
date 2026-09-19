package dev.langchain4j.model.judge;

import dev.langchain4j.Experimental;

/** A typed question evaluated by a {@link JudgeModel}. */
@Experimental
public sealed interface Question permits NoulQuestion, ChoiceQuestion, ScoreQuestion {

    String instructions();
}
