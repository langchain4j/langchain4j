package dev.langchain4j.model.deliverance;

import dev.langchain4j.model.deliverance.spi.DeliveranceLanguageModelBuilderFactory;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import io.teknek.deliverance.generator.GeneratorParameters;
import io.teknek.deliverance.model.AbstractModel;
import io.teknek.deliverance.model.AutoModelForCausaLm;
import io.teknek.deliverance.safetensors.prompt.PromptContext;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class DeliveranceLanguageModel implements LanguageModel {

    private final AbstractModel model;
    private final GeneratorParameters defaultGeneratorParameters;
    private final UUID id = UUID.randomUUID();

    public DeliveranceLanguageModel(AbstractModel model, GeneratorParameters defaultGeneratorParameters) {
        this.model = model;
        this.defaultGeneratorParameters = defaultGeneratorParameters;
    }

    public static DeliveranceLanguageModelBuilder builder() {
        for (DeliveranceLanguageModelBuilderFactory factory : loadFactories(DeliveranceLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new DeliveranceLanguageModelBuilder();
    }

    @Override
    public Response<String> generate(String prompt) {
        io.teknek.deliverance.generator.Response response = model.generate(
                id,
                PromptContext.of(prompt),
                DeliveranceModelSupport.copy(defaultGeneratorParameters),
                (token, nextRaw, nextCleaned, timing) -> {
                }
        );

        return Response.from(
                response.responseText,
                DeliveranceModelSupport.toTokenUsage(response),
                DeliveranceModelSupport.toFinishReason(response.finishReason)
        );
    }

    public static class DeliveranceLanguageModelBuilder {

        private AutoModelForCausaLm.Builder modelBuilder;
        private GeneratorParameters generatorParameters = new GeneratorParameters();

        public DeliveranceLanguageModelBuilder() {
        }

        public DeliveranceLanguageModelBuilder modelBuilder(AutoModelForCausaLm.Builder modelBuilder) {
            this.modelBuilder = modelBuilder;
            return this;
        }

        public DeliveranceLanguageModelBuilder generatorParameters(GeneratorParameters generatorParameters) {
            this.generatorParameters = DeliveranceModelSupport.copy(generatorParameters);
            return this;
        }

        public DeliveranceLanguageModelBuilder customizeGeneratorParameters(Consumer<GeneratorParameters> consumer) {
            consumer.accept(this.generatorParameters);
            return this;
        }

        public DeliveranceLanguageModel build() {
            AutoModelForCausaLm.Builder builder = Objects.requireNonNull(modelBuilder, "modelBuilder must be set");
            return new DeliveranceLanguageModel(
                    DeliveranceModelSupport.loadGenerationModel(builder),
                    DeliveranceModelSupport.copy(generatorParameters)
            );
        }

        @Override
        public String toString() {
            return "DeliveranceLanguageModel.DeliveranceLanguageModelBuilder(modelBuilder=" + modelBuilder
                    + ", generatorParameters=" + generatorParameters + ")";
        }
    }
}
