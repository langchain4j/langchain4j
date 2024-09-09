package dev.langchain4j.model.dashscope.spi;

import dev.langchain4j.model.dashscope.QwenTokenizer;

import java.util.function.Supplier;

public interface QwenTokenizerBuilderFactory extends Supplier<QwenTokenizer.QwenTokenizerBuilder> {
}