package dev.langchain4j.model.openai.spi;

import dev.langchain4j.Internal;
import dev.langchain4j.model.openai.OpenAiAudioModel;
import java.util.function.Supplier;

/**
 * A factory for building {@link OpenAiAudioModel.OpenAiAudioModelBuilder} instances.
 */
@Internal
public interface OpenAiAudioModelBuilderFactory extends Supplier<OpenAiAudioModel.OpenAiAudioModelBuilder> {}
