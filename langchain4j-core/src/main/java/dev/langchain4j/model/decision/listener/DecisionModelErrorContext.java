package dev.langchain4j.model.decision.listener;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.request.DecisionRequest;
import java.util.Map;

/**
 * The context of an error of a decision model: the error, the corresponding request, the model provider and
 * attributes.
 * The attributes can be used to pass data between the methods of a {@link DecisionModelListener}, or between
 * multiple listeners.
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionModelErrorContext {

    private final Throwable error;
    private final DecisionRequest decisionRequest;
    private final ModelProvider modelProvider;
    private final Map<Object, Object> attributes;

    public DecisionModelErrorContext(
            Throwable error,
            DecisionRequest decisionRequest,
            ModelProvider modelProvider,
            Map<Object, Object> attributes) {
        this.error = ensureNotNull(error, "error");
        this.decisionRequest = ensureNotNull(decisionRequest, "decisionRequest");
        this.modelProvider = ensureNotNull(modelProvider, "modelProvider");
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    public Throwable error() {
        return error;
    }

    public DecisionRequest decisionRequest() {
        return decisionRequest;
    }

    public ModelProvider modelProvider() {
        return modelProvider;
    }

    public Map<Object, Object> attributes() {
        return attributes;
    }
}
