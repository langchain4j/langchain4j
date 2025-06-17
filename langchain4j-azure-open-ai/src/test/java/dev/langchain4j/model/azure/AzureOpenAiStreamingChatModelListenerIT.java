package dev.langchain4j.model.azure;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;

import static java.util.Collections.singletonList;

class AzureOpenAiStreamingChatModelListenerIT extends AbstractStreamingChatModelListenerIT {

    @Override
    protected StreamingChatModel createModel(ChatModelListener listener) {
        return AzureOpenAiStreamingChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(modelName())
                .temperature(temperature())
                .topP(topP())
                .maxTokens(maxTokens())
                .tokenCountEstimator(new AzureOpenAiTokenCountEstimator(modelName()))
                .logRequestsAndResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return "gpt-4o-mini";
    }

    @Override
    protected StreamingChatModel createFailingModel(ChatModelListener listener) {
        return AzureOpenAiStreamingChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey("banana")
                .deploymentName(modelName())
                .logRequestsAndResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return AuthenticationException.class;
    }
}
