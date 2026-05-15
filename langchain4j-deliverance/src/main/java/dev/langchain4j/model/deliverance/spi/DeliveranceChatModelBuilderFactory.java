package dev.langchain4j.model.deliverance.spi;

import dev.langchain4j.model.deliverance.DeliveranceChatModel;

import java.util.function.Supplier;

public interface DeliveranceChatModelBuilderFactory extends Supplier<DeliveranceChatModel.DeliveranceChatModelBuilder> {
}
