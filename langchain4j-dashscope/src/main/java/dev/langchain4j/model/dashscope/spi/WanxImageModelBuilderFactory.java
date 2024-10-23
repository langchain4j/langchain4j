package dev.langchain4j.model.dashscope.spi;

import dev.langchain4j.model.dashscope.WanxImageModel;

import java.util.function.Supplier;

public interface WanxImageModelBuilderFactory extends Supplier<WanxImageModel.WanxImageModelBuilder> {
}
