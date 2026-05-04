package dev.langchain4j.model.deliverance;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.deliverance.spi.DeliveranceChatModelBuilderFactory;
import io.teknek.deliverance.generator.GeneratorParameters;
import io.teknek.deliverance.model.AbstractModel;
import io.teknek.deliverance.model.AutoModelForCausaLm;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class DeliveranceChatModel implements ChatModel {

    private final AbstractModel model;
    private final String modelName;
    private final DeliveranceChatRequestParameters defaultRequestParameters;
    private final UUID id = UUID.randomUUID();

    public DeliveranceChatModel(AbstractModel model,
                                String modelName,
                                DeliveranceChatRequestParameters defaultRequestParameters) {
        this.model = model;
        this.modelName = modelName;
        this.defaultRequestParameters = defaultRequestParameters;
    }

    public static DeliveranceChatModelBuilder builder() {
        for (DeliveranceChatModelBuilderFactory factory : loadFactories(DeliveranceChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new DeliveranceChatModelBuilder();
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        ChatRequestValidationUtils.validateMessages(chatRequest.messages());
        DeliveranceChatRequestParameters parameters = DeliveranceModelSupport.toDeliveranceChatRequestParameters(chatRequest.parameters());
        DeliveranceChatRequestValidator.validate(parameters);

        GeneratorParameters generatorParameters = DeliveranceModelSupport.toGeneratorParameters(parameters);
        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();

        io.teknek.deliverance.generator.Response response = model.generate(
                id,
                DeliveranceModelSupport.toPromptContext(model, chatRequest.messages(), toolSpecifications),
                generatorParameters,
                (token, nextRaw, nextCleaned, timing) -> {
                }
        );

        return DeliveranceModelSupport.toChatResponse(response, modelName);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    public static class DeliveranceChatModelBuilder {

        private AutoModelForCausaLm.Builder modelBuilder;
        private final DeliveranceChatRequestParameters.Builder<?> defaultRequestParametersBuilder = DeliveranceChatRequestParameters.builder();

        public DeliveranceChatModelBuilder() {
        }

        public DeliveranceChatModelBuilder modelBuilder(AutoModelForCausaLm.Builder modelBuilder) {
            this.modelBuilder = modelBuilder;
            return this;
        }

        public DeliveranceChatModelBuilder defaultRequestParameters(DeliveranceChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParametersBuilder.overrideWith(defaultRequestParameters);
            return this;
        }

        public DeliveranceChatModelBuilder defaultRequestParameters(Consumer<DeliveranceChatRequestParameters.Builder<?>> consumer) {
            consumer.accept(defaultRequestParametersBuilder);
            return this;
        }

        public DeliveranceChatModel build() {
            AutoModelForCausaLm.Builder builder = Objects.requireNonNull(modelBuilder, "modelBuilder must be set");
            return new DeliveranceChatModel(
                    DeliveranceModelSupport.loadGenerationModel(builder),
                    DeliveranceModelSupport.modelName(builder),
                    defaultRequestParametersBuilder.build()
            );
        }

        @Override
        public String toString() {
            return "DeliveranceChatModel.DeliveranceChatModelBuilder(modelBuilder=" + modelBuilder
                    + ", defaultRequestParameters=" + defaultRequestParametersBuilder.build() + ")";
        }
    }
}
