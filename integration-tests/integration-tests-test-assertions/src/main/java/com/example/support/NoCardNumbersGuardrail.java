package com.example.support;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import java.util.regex.Pattern;

/**
 * Stops messages that contain a payment card number before they reach the model.
 */
public class NoCardNumbersGuardrail implements InputGuardrail {

    private static final Pattern CARD_NUMBER = Pattern.compile("\\b(?:\\d[ -]?){13,16}\\b");

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        if (CARD_NUMBER.matcher(userMessage.singleText()).find()) {
            return fatal("The message contains a payment card number");
        }
        return success();
    }
}
