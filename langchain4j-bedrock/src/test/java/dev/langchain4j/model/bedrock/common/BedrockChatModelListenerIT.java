package dev.langchain4j.model.bedrock.common;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.AfterEach;

import java.util.List;

import static dev.langchain4j.model.bedrock.common.BedrockAiServicesIT.sleepIfNeeded;

class BedrockChatModelListenerIT extends AbstractChatModelListenerIT {

    @Override
    protected ChatModel createModel(ChatModelListener listener) {
        return BedrockChatModel.builder()
                .modelId("us.amazon.nova-lite-v1:0")
                .defaultRequestParameters(BedrockChatRequestParameters.builder()
                        .temperature(temperature())
                        .topP(topP())
                        .maxOutputTokens(maxTokens())
                        .build())
                .listeners(List.of(listener))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    protected String modelName() {
        return "us.amazon.nova-lite-v1:0";
    }

    @Override
    protected ChatModel createFailingModel(ChatModelListener listener) {
        return BedrockChatModel.builder()
                .modelId("banana")
                .maxRetries(0)
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return dev.langchain4j.exception.InvalidRequestException.class;
    }

    @AfterEach
    void afterEach() {
        sleepIfNeeded();
    }
}
