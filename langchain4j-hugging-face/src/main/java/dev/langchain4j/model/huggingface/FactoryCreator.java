package dev.langchain4j.model.huggingface;

import dev.langchain4j.model.huggingface.client.HuggingFaceClient;
import dev.langchain4j.model.huggingface.spi.HuggingFaceClientFactory;
import dev.langchain4j.spi.ServiceHelper;
import java.util.Collection;

class FactoryCreator {

    static final HuggingFaceClientFactory FACTORY = factory();

    private static HuggingFaceClientFactory factory() {
        Collection<HuggingFaceClientFactory> factories =
                ServiceHelper.loadFactories(HuggingFaceClientFactory.class);
        for (HuggingFaceClientFactory factory : factories) {
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
