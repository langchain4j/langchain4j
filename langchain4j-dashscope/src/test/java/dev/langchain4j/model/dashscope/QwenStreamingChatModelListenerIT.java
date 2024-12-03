package dev.langchain4j.model.dashscope;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static dev.langchain4j.model.dashscope.QwenModelName.QWEN_MAX;
import static dev.langchain4j.model.dashscope.QwenTestHelper.apiKey;
import static java.util.Collections.singletonList;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class QwenStreamingChatModelListenerIT extends StreamingChatModelListenerIT {
    @Override
    protected StreamingChatLanguageModel createModel(ChatModelListener listener) {
        return QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName())
                .temperature(temperature().floatValue())
                .topP(topP())
                .maxTokens(maxTokens())
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return QWEN_MAX;
    }

    @Override
    protected StreamingChatLanguageModel createFailingModel(ChatModelListener listener) {
        return QwenStreamingChatModel.builder()
                .apiKey("banana")
                .modelName(modelName())
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return com.alibaba.dashscope.exception.ApiException.class;
    }
}
