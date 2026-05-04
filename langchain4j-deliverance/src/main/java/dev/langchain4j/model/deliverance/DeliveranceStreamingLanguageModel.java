package dev.langchain4j.model.deliverance;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.deliverance.spi.DeliveranceStreamingLanguageModelBuilderFactory;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import io.teknek.deliverance.generator.GeneratorParameters;
import io.teknek.deliverance.model.AbstractModel;
import io.teknek.deliverance.model.AutoModelForCausaLm;
import io.teknek.deliverance.safetensors.prompt.PromptContext;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class DeliveranceStreamingLanguageModel implements StreamingLanguageModel {

    private final AbstractModel model;
    private final GeneratorParameters defaultGeneratorParameters;
    private final UUID id = UUID.randomUUID();

    public DeliveranceStreamingLanguageModel(AbstractModel model, GeneratorParameters defaultGeneratorParameters) {
        this.model = model;
        this.defaultGeneratorParameters = defaultGeneratorParameters;
    }

    public static DeliveranceStreamingLanguageModelBuilder builder() {
        for (DeliveranceStreamingLanguageModelBuilderFactory factory : loadFactories(DeliveranceStreamingLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new DeliveranceStreamingLanguageModelBuilder();
    }

    @Override
    public void generate(String prompt, StreamingResponseHandler<String> handler) {
        try {
            io.teknek.deliverance.generator.Response response = model.generate(
                    id,
                    PromptContext.of(prompt),
                    DeliveranceModelSupport.copy(defaultGeneratorParameters),
                    (token, nextRaw, nextCleaned, timing) -> handler.onNext(nextCleaned)
            );

            handler.onComplete(Response.from(
                    response.responseText,
                    DeliveranceModelSupport.toTokenUsage(response),
                    DeliveranceModelSupport.toFinishReason(response.finishReason)
            ));
        } catch (Throwable t) {
            handler.onError(t);
        }
    }

    public static class DeliveranceStreamingLanguageModelBuilder {

        private AutoModelForCausaLm.Builder modelBuilder;
        private GeneratorParameters generatorParameters = new GeneratorParameters();

        public DeliveranceStreamingLanguageModelBuilder() {
        }

        public DeliveranceStreamingLanguageModelBuilder modelBuilder(AutoModelForCausaLm.Builder modelBuilder) {
            this.modelBuilder = modelBuilder;
            return this;
        }

        public DeliveranceStreamingLanguageModelBuilder generatorParameters(GeneratorParameters generatorParameters) {
            this.generatorParameters = DeliveranceModelSupport.copy(generatorParameters);
            return this;
        }

        public DeliveranceStreamingLanguageModelBuilder customizeGeneratorParameters(Consumer<GeneratorParameters> consumer) {
            consumer.accept(this.generatorParameters);
            return this;
        }

        public DeliveranceStreamingLanguageModel build() {
            AutoModelForCausaLm.Builder builder = Objects.requireNonNull(modelBuilder, "modelBuilder must be set");
            return new DeliveranceStreamingLanguageModel(
                    DeliveranceModelSupport.loadGenerationModel(builder),
                    DeliveranceModelSupport.copy(generatorParameters)
            );
        }

        @Override
        public String toString() {
            return "DeliveranceStreamingLanguageModel.DeliveranceStreamingLanguageModelBuilder(modelBuilder=" + modelBuilder
                    + ", generatorParameters=" + generatorParameters + ")";
        }
    }
}
