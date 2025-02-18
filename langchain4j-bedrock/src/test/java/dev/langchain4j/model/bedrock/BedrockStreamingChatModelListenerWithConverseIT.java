package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.concurrent.CompletionException;

import static java.util.Collections.singletonList;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockStreamingChatModelListenerWithConverseIT extends StreamingChatModelListenerIT {

    @Override
    protected StreamingChatLanguageModel createModel(ChatModelListener listener) {
        return BedrockStreamingChatModel.builder()
                .modelId(modelName())
                .defaultRequestParameters(
                        DefaultChatRequestParameters.builder()
                                .modelName(modelName())
                                .temperature(temperature())
                                .topP(topP())
                                .maxOutputTokens(maxTokens())
                                .build()
                )
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return "us.amazon.nova-lite-v1:0";
    }

    @Override
    protected StreamingChatLanguageModel createFailingModel(ChatModelListener listener) {
        return BedrockStreamingChatModel.builder()
                .modelId("banana")
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return CompletionException.class;
    }
}
