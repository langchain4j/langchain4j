package dev.langchain4j.model.huggingface;

import dev.langchain4j.model.huggingface.client.HuggingFaceClient;
import dev.langchain4j.model.huggingface.spi.HuggingFaceClientFactory;
import dev.langchain4j.spi.ServiceHelper;

class FactoryCreator {

    static final HuggingFaceClientFactory FACTORY = ServiceHelper.loadService(
            HuggingFaceClientFactory.class, DefaultHuggingFaceClientFactory::new);

    static class DefaultHuggingFaceClientFactory implements HuggingFaceClientFactory {

        @Override
        public HuggingFaceClient create(Input input) {
            return new DefaultHuggingFaceClient(input.apiKey(), input.modelId(), input.timeout());
        }
    }
}
