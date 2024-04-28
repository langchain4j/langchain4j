package dev.langchain4j.model.sensenova.spi;


import dev.langchain4j.model.sensenova.SenseNovaChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link SenseNovaChatModel.SenseNovaChatModelBuilder} instances.
 */
public interface SenseNovaChatModelBuilderFactory extends Supplier<SenseNovaChatModel.SenseNovaChatModelBuilder> {
}
