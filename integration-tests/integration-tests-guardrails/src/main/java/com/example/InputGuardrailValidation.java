package com.example;

import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import java.util.Map;

public class InputGuardrailValidation implements InputGuardrail {
    private static final InputGuardrailValidation INSTANCE = new InputGuardrailValidation();
    private InputGuardrailRequest params;

    private InputGuardrailValidation() {}

    public static InputGuardrailValidation getInstance() {
        return INSTANCE;
    }

    public InputGuardrailResult validate(InputGuardrailRequest params) {
        this.params = params;
        return success();
    }

    public void reset() {
        this.params = null;
    }

    public String spyUserMessageTemplate() {
        return params.requestParams().userMessageTemplate();
    }

    public String spyUserMessageText() {
        return params.userMessage().singleText();
    }

    public Map<String, Object> spyVariables() {
        return params.requestParams().variables();
    }
}
