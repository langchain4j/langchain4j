package dev.langchain4j.model.deliverance;

import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.request.ChatRequestParameters;

class DeliveranceChatRequestValidator {

    private DeliveranceChatRequestValidator() {
    }

    static void validate(ChatRequestParameters parameters) {
        String errorTemplate = "%s is not supported yet by this model provider";

        if (parameters.modelName() != null) {
            throw new UnsupportedFeatureException(String.format(errorTemplate, "'modelName' parameter"));
        }
        if (parameters.frequencyPenalty() != null) {
            throw new UnsupportedFeatureException(String.format(errorTemplate, "'frequencyPenalty' parameter try xtc"));
        }
        if (parameters.presencePenalty() != null) {
            throw new UnsupportedFeatureException(String.format(errorTemplate, "'presencePenalty' parameter try xtc"));
        }

        ChatRequestValidationUtils.validate(parameters.toolChoice());
        ChatRequestValidationUtils.validate(parameters.responseFormat());
    }
}
