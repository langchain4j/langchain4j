package dev.langchain4j.service.context;

import dev.langchain4j.model.chat.ChatLanguageModel;

public interface ChatLanguageModelContext extends BaseAiServiceContext {

    ChatLanguageModel getChatLanguageModel();

}
