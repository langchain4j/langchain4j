package dev.langchain4j.service.openai.common;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;

import java.util.List;

import static dev.langchain4j.service.openai.common.OpenAiChatModelIT.OPEN_AI_CHAT_MODEL_BUILDER;

// TODO move to langchain4j-open-ai module once dependency cycle is resolved
class OpenAiAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatLanguageModel> models() {
        // TODO more configs?
        return List.of(OPEN_AI_CHAT_MODEL_BUILDER.build());
    }
}
