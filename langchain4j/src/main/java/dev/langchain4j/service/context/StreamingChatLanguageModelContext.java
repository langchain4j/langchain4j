package dev.langchain4j.service.context;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;

public interface StreamingChatLanguageModelContext extends BaseAiServiceContext {

    StreamingChatLanguageModel getStreamingChatLanguageModel();
}
