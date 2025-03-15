package dev.langchain4j.model.novitaai.spi;

import dev.langchain4j.model.novitaai.NovitaAiChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link NovitaAiChatModel.Builder} instances.
 */
public interface NovitaAiChatModelBuilderFactory extends Supplier<NovitaAiChatModel.Builder> {
}
