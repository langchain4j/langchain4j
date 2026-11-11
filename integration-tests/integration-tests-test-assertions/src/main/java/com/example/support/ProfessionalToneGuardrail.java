package com.example.support;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import java.util.List;
import java.util.Locale;

/**
 * Asks the model to rewrite responses that use slang.
 */
public class ProfessionalToneGuardrail implements OutputGuardrail {

    private static final List<String> SLANG = List.of("dude", "gonna", "wanna");

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        String text = responseFromLLM.text() == null ? "" : responseFromLLM.text().toLowerCase(Locale.ROOT);
        if (SLANG.stream().anyMatch(text::contains)) {
            return reprompt("The response uses slang", "Rewrite your response in a professional tone, without slang.");
        }
        return success();
    }
}
