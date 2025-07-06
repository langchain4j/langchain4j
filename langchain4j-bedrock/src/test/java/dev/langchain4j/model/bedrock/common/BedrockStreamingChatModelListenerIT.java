package dev.langchain4j.model.bedrock.common;

import static dev.langchain4j.model.bedrock.common.BedrockAiServicesIT.sleepIfNeeded;
import static java.util.Collections.singletonList;

import dev.langchain4j.model.bedrock.BedrockStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockStreamingChatModelListenerIT extends AbstractStreamingChatModelListenerIT {

    @Override
    protected StreamingChatModel createModel(ChatModelListener listener) {
        return BedrockStreamingChatModel.builder()
                .modelId(modelName())
                .defaultRequestParameters(DefaultChatRequestParameters.builder()
                        .modelName(modelName())
                        .temperature(temperature())
                        .topP(topP())
                        .maxOutputTokens(maxTokens())
                        .build())
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
    protected StreamingChatModel createFailingModel(ChatModelListener listener) {
        return BedrockStreamingChatModel.builder()
                .modelId("banana")
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
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
