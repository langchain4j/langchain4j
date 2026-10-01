package dev.langchain4j.model.decision.listener;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.request.DecisionRequest;
import java.util.Map;

/**
 * The context of a request to a decision model: the request, the model provider and attributes.
 * The attributes can be used to pass data between the methods of a {@link DecisionModelListener}, or between
 * multiple listeners.
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionModelRequestContext {

    private final DecisionRequest decisionRequest;
    private final ModelProvider modelProvider;
    private final Map<Object, Object> attributes;

    public DecisionModelRequestContext(
            DecisionRequest decisionRequest,
            ModelProvider modelProvider,
            Map<Object, Object> attributes) {
        this.decisionRequest = ensureNotNull(decisionRequest, "decisionRequest");
        this.modelProvider = ensureNotNull(modelProvider, "modelProvider");
        this.attributes = ensureNotNull(attributes, "attributes");
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
