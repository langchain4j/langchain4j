package com.example.support;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(
        chatLanguageModelSupplier = TestModels.ScriptedChatModel.class,
        tools = BookingTools.class,
        retrievalAugmentor = PolicyAugmentor.class)
public interface SupportAssistant {

    @SystemMessage("You are a customer support agent of the car rental company Miles of Smiles.")
    String chat(@MemoryId String customerId, @UserMessage String message);
}
