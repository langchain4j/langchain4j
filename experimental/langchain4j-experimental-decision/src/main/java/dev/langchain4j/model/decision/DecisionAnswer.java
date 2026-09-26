package dev.langchain4j.model.decision;

import dev.langchain4j.Experimental;
import java.util.Map;

/** A provider-extensible answer to a named {@link Question}. */
@Experimental
public interface DecisionAnswer {

    Object value();

    Double confidence();

    ConfidenceProvenance confidenceProvenance();

    Map<String, Object> metadata();
}
