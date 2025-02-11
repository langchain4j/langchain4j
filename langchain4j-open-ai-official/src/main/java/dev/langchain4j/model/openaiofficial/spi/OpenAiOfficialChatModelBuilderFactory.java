package dev.langchain4j.model.openaiofficial.spi;

import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import java.util.function.Supplier;

/**
 * A factory for building {@link dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel} instances.
 */
public interface OpenAiOfficialChatModelBuilderFactory
        extends Supplier<OpenAiOfficialChatModel.OpenAiOfficialChatModelBuilder> {}
