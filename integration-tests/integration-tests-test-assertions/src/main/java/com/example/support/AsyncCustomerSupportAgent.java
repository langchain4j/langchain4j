package com.example.support;

import dev.langchain4j.service.SystemMessage;
import java.util.concurrent.CompletableFuture;

@SystemMessage("You are the customer support agent of Miles of Smiles car rental. Be polite and concise.")
public interface AsyncCustomerSupportAgent {

    CompletableFuture<String> chat(String message);
}
