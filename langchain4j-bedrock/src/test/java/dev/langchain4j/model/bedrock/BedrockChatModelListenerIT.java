package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;

import static dev.langchain4j.model.bedrock.BedrockAnthropicMessageChatModel.Types.AnthropicClaude3SonnetV1;
import static java.util.Collections.singletonList;

class BedrockChatModelListenerIT extends AbstractChatModelListenerIT {

    @Override
    protected ChatModel createModel(ChatModelListener listener) {
        return BedrockAnthropicMessageChatModel.builder()
                .model(modelName())
                .temperature(temperature())
                .topP(topP().floatValue())
                .maxTokens(maxTokens())
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return AnthropicClaude3SonnetV1.getValue();
    }

    @Override
    protected ChatModel createFailingModel(ChatModelListener listener) {
        return BedrockAnthropicMessageChatModel.builder()
                .model("banana")
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return software.amazon.awssdk.services.bedrockruntime.model.ValidationException.class;
    }
}
