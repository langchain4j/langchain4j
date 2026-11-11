package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.OLLAMA_BASE_URL;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static dev.langchain4j.model.ollama.OllamaImage.TEV1;
import static dev.langchain4j.model.ollama.OllamaImage.localOllamaImage;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.common.AbstractDecisionModelIT;
import dev.langchain4j.model.decision.listener.DecisionModelListener;

class OllamaDecisionModelIT extends AbstractDecisionModelIT {

    static LC4jOllamaContainer ollama;

    static {
        if (isNullOrEmpty(OLLAMA_BASE_URL)) {
            String localOllamaImage = localOllamaImage(TEV1);
            ollama = new LC4jOllamaContainer(OllamaImage.resolve(OllamaImage.OLLAMA_IMAGE, localOllamaImage))
                    .withModel(TEV1);
            ollama.start();
            ollama.commitToImage(localOllamaImage);
        }
    }

    static OllamaDecisionModel.OllamaDecisionModelBuilder builder() {
        return OllamaDecisionModel.builder().baseUrl(ollamaBaseUrl(ollama)).modelName(TEV1);
    }

    @Override
    protected DecisionModel model() {
        return builder().logRequests(true).logResponses(true).build();
    }

    @Override
    protected DecisionModel modelWithListener(DecisionModelListener listener) {
        return builder().listeners(listener).build();
    }

    @Override
    protected boolean reportsProbabilities() {
        return true;
    }

    @Override
    protected boolean reportsTokenUsage() {
        return true;
    }

    @Override
    protected String requestModelName() {
        return TEV1;
    }
}
