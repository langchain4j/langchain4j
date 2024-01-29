package dev.langchain4j.model.qianfan.spi;



import dev.langchain4j.model.qianfan.QianfanStreamingChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link QianfanStreamingChatModel.QianfanStreamingChatModelBuilder} instances.
 */
public interface QianfanStreamingChatModelBuilderFactory extends Supplier<QianfanStreamingChatModel.QianfanStreamingChatModelBuilder> {
}
