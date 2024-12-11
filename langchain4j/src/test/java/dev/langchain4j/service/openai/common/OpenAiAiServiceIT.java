package dev.langchain4j.service.openai.common;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;

import java.util.List;

import static dev.langchain4j.service.openai.common.OpenAiChatModelIT.defaultModelBuilder;

// TODO move to langchain4j-open-ai module once dependency cycle is resolved
class OpenAiAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                defaultModelBuilder().build()
                // TODO more configs?
        );
    }
}
