package dev.langchain4j.model.deliverance;

import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.deliverance.spi.DeliveranceStreamingChatModelBuilderFactory;
import io.teknek.deliverance.generator.GeneratorParameters;
import io.teknek.deliverance.model.AbstractModel;
import io.teknek.deliverance.model.AutoModelForCausaLm;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class DeliveranceStreamingChatModel implements StreamingChatModel {

    private final AbstractModel model;
    private final String modelName;
    private final DeliveranceChatRequestParameters defaultRequestParameters;
    private final UUID id = UUID.randomUUID();

    public DeliveranceStreamingChatModel(AbstractModel model,
                                         String modelName,
                                         DeliveranceChatRequestParameters defaultRequestParameters) {
        this.model = model;
        this.modelName = modelName;
        this.defaultRequestParameters = defaultRequestParameters;
    }

    public static DeliveranceStreamingChatModelBuilder builder() {
        for (DeliveranceStreamingChatModelBuilderFactory factory : loadFactories(DeliveranceStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new DeliveranceStreamingChatModelBuilder();
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ChatRequestValidationUtils.validateMessages(chatRequest.messages());
        DeliveranceChatRequestParameters parameters = DeliveranceModelSupport.toDeliveranceChatRequestParameters(chatRequest.parameters());
        DeliveranceChatRequestValidator.validate(parameters);

        GeneratorParameters generatorParameters = DeliveranceModelSupport.toGeneratorParameters(parameters);

        try {
            io.teknek.deliverance.generator.Response response = model.generate(
                    id,
                    DeliveranceModelSupport.toPromptContext(model, chatRequest.messages(), parameters.toolSpecifications()),
                    generatorParameters,
                    (token, nextRaw, nextCleaned, timing) -> handler.onPartialResponse(nextCleaned)
            );
            handler.onCompleteResponse(DeliveranceModelSupport.toChatResponse(response, modelName));
        } catch (Throwable t) {
            handler.onError(t);
        }
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    public static class DeliveranceStreamingChatModelBuilder {

        private AutoModelForCausaLm.Builder modelBuilder;
        private final DeliveranceChatRequestParameters.Builder<?> defaultRequestParametersBuilder = DeliveranceChatRequestParameters.builder();

        public DeliveranceStreamingChatModelBuilder() {
        }

        public DeliveranceStreamingChatModelBuilder modelBuilder(AutoModelForCausaLm.Builder modelBuilder) {
            this.modelBuilder = modelBuilder;
            return this;
        }

        public DeliveranceStreamingChatModelBuilder defaultRequestParameters(DeliveranceChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParametersBuilder.overrideWith(defaultRequestParameters);
            return this;
        }

        public DeliveranceStreamingChatModelBuilder defaultRequestParameters(Consumer<DeliveranceChatRequestParameters.Builder<?>> consumer) {
            consumer.accept(defaultRequestParametersBuilder);
            return this;
        }

        public DeliveranceStreamingChatModel build() {
            AutoModelForCausaLm.Builder builder = Objects.requireNonNull(modelBuilder, "modelBuilder must be set");
            return new DeliveranceStreamingChatModel(
                    DeliveranceModelSupport.loadGenerationModel(builder),
                    DeliveranceModelSupport.modelName(builder),
                    defaultRequestParametersBuilder.build()
            );
        }

        @Override
        public String toString() {
            return "DeliveranceStreamingChatModel.DeliveranceStreamingChatModelBuilder(modelBuilder=" + modelBuilder
                    + ", defaultRequestParameters=" + defaultRequestParametersBuilder.build() + ")";
        }
    }
}
