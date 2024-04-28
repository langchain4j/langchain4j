package dev.langchain4j.model.sensenova.spi;

import dev.langchain4j.model.sensenova.SenseNovaStreamingChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link SenseNovaStreamingChatModel.SenseNovaStreamingChatModelBuilder} instances.
 */
public interface SenseNovaStreamingChatModelBuilderFactory extends Supplier<SenseNovaStreamingChatModel.SenseNovaStreamingChatModelBuilder> {
}
