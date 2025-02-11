package dev.langchain4j.model.openaiofficial.spi;

import dev.langchain4j.model.openaiofficial.OpenAiOfficialStreamingChatModel;
import java.util.function.Supplier;

/**
 * A factory for building {@link dev.langchain4j.model.openaiofficial.OpenAiOfficialStreamingChatModel} instances.
 */
public interface OpenAiOfficialStreamingChatModelBuilderFactory
        extends Supplier<OpenAiOfficialStreamingChatModel.OpenAiOfficialStreamingChatModelBuilder> {}
