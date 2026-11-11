package com.example.support;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

@SystemMessage("You are the customer support agent of Miles of Smiles car rental. Be polite and concise.")
public interface CustomerSupportAgent {

    String chat(@MemoryId String customerId, @UserMessage String message);

    Result<String> chatWithDetails(@MemoryId String customerId, @UserMessage String message);

    void handleInBackground(@MemoryId String customerId, @UserMessage String message);
}
