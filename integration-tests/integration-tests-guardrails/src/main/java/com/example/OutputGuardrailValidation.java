package com.example;

import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import java.util.Map;

public class OutputGuardrailValidation implements OutputGuardrail {
    private static final OutputGuardrailValidation INSTANCE = new OutputGuardrailValidation();
    private OutputGuardrailRequest params;

    private OutputGuardrailValidation() {}

    public static OutputGuardrailValidation getInstance() {
        return INSTANCE;
    }

    public OutputGuardrailResult validate(OutputGuardrailRequest params) {
        this.params = params;
        return success();
    }

    public void reset() {
        this.params = null;
    }

    public String spyUserMessageTemplate() {
        return params.requestParams().userMessageTemplate();
    }

    public Map<String, Object> spyVariables() {
        return params.requestParams().variables();
    }
}
