package dev.langchain4j.model.decision.listener;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.util.Map;

/**
 * The context of a response from a decision model: the response, the corresponding request, the model
 * provider and attributes.
 * The attributes can be used to pass data between the methods of a {@link DecisionModelListener}, or between
 * multiple listeners.
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionModelResponseContext {

    private final DecisionResponse decisionResponse;
    private final DecisionRequest decisionRequest;
    private final ModelProvider modelProvider;
    private final Map<Object, Object> attributes;

    public DecisionModelResponseContext(
            DecisionResponse decisionResponse,
            DecisionRequest decisionRequest,
            ModelProvider modelProvider,
            Map<Object, Object> attributes) {
        this.decisionResponse = ensureNotNull(decisionResponse, "decisionResponse");
        this.decisionRequest = ensureNotNull(decisionRequest, "decisionRequest");
        this.modelProvider = ensureNotNull(modelProvider, "modelProvider");
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    public DecisionResponse decisionResponse() {
        return decisionResponse;
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
