package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.BedrockChatModelWithInvokeAPIIT.sleepIfNeeded;
import static java.util.Collections.singletonList;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.AfterEach;

import java.util.List;

class BedrockChatModelListenerWithConverseIT extends AbstractChatModelListenerIT {

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
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return software.amazon.awssdk.services.bedrockruntime.model.ValidationException.class;
    }

    @AfterEach
    void afterEach() {
        sleepIfNeeded();
    }
}
