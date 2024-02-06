package dev.langchain4j.model.huggingface;

import dev.langchain4j.model.huggingface.client.HuggingFaceClient;
import dev.langchain4j.model.huggingface.spi.HuggingFaceClientFactory;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

class FactoryCreator {

    static final HuggingFaceClientFactory FACTORY = factory();

    private static HuggingFaceClientFactory factory() {
        for (HuggingFaceClientFactory factory : loadFactories(HuggingFaceClientFactory.class)) {
            return factory;
        }
        return new DefaultHuggingFaceClientFactory();
    }

    static class DefaultHuggingFaceClientFactory implements HuggingFaceClientFactory {

        @Override
        public HuggingFaceClient create(Input input) {
            return new DefaultHuggingFaceClient(input.apiKey(), input.modelId(), input.timeout());
        }
    }
}
