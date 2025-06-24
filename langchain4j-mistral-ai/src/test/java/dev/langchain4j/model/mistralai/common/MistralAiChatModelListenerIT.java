package dev.langchain4j.model.mistralai.common;

import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_SMALL_LATEST;

import java.util.List;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.mistralai.MistralAiChatModel;

class MistralAiChatModelListenerIT extends AbstractChatModelListenerIT {

    @Override
    protected ChatModel createModel(ChatModelListener listener) {
        return MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(modelName())
                .temperature(temperature())
                .topP(topP())
                .maxTokens(maxTokens())
                .logRequests(true)
                .logResponses(true)
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return MISTRAL_SMALL_LATEST.toString();
    }

    @Override
    protected ChatModel createFailingModel(ChatModelListener listener) {
        return MistralAiChatModel.builder()
                .apiKey("banana")
                .maxRetries(0)
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return dev.langchain4j.exception.AuthenticationException.class;
    }
}
