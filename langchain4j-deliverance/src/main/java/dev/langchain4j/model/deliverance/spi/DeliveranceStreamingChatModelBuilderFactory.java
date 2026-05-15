package dev.langchain4j.model.deliverance.spi;

import dev.langchain4j.model.deliverance.DeliveranceStreamingChatModel;

import java.util.function.Supplier;

public interface DeliveranceStreamingChatModelBuilderFactory extends Supplier<DeliveranceStreamingChatModel.DeliveranceStreamingChatModelBuilder> {
}
