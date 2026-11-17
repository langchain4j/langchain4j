package com.example.support;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class NoInternalNotesGuardrail implements OutputGuardrail {

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        if (responseFromLLM.text() != null && responseFromLLM.text().contains("INTERNAL")) {
            return fatal("The answer contains internal notes");
        }
        return success();
    }

    @Override
    public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
        return CompletableFuture.completedFuture(validate(request));
    }
}
