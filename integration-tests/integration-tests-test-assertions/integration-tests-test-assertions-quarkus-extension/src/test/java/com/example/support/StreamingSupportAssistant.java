package com.example.support;

import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;

@RegisterAiService(streamingChatLanguageModelSupplier = TestModels.StreamedAnswerModel.class)
public interface StreamingSupportAssistant {

    Multi<String> stream(@UserMessage String message);

    TokenStream chat(@UserMessage String message);
}
