package com.example;

import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailParams;
import dev.langchain4j.guardrail.InputGuardrailResult;
import java.util.Map;

public class GuardrailValidation implements InputGuardrail {
    private static final GuardrailValidation INSTANCE = new GuardrailValidation();
    private InputGuardrailParams params;

    private GuardrailValidation() {}

    public static GuardrailValidation getInstance() {
        return INSTANCE;
    }

    public InputGuardrailResult validate(InputGuardrailParams params) {
        this.params = params;
        return success();
    }

    public void reset() {
        this.params = null;
    }

    public String spyUserMessageTemplate() {
        return params.userMessageTemplate();
    }

    public String spyUserMessageText() {
        return params.userMessage().singleText();
    }

    public Map<String, Object> spyVariables() {
        return params.variables();
    }
}
