package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.config.InputGuardrailsConfig;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GuardrailService {
    private InputGuardrailsConfig inputGuardrailsConfig;
    private OutputGuardrailsConfig outputGuardrailsConfig;
    private List<InputGuardrail> inputGuardrails = new ArrayList<>();
    private List<OutputGuardrail> outputGuardrails = new ArrayList<>();

    public void inputGuardrailsConfig(InputGuardrailsConfig inputGuardrailsConfig) {
        this.inputGuardrailsConfig = ensureNotNull(inputGuardrailsConfig, "inputGuardrailsConfig");
    }

    public void outputGuardrailsConfig(OutputGuardrailsConfig outputGuardrailsConfig) {
        this.outputGuardrailsConfig = ensureNotNull(outputGuardrailsConfig, "outputGuardrailsConfig");
    }

    public void inputGuardrails(InputGuardrail... inputGuardrails) {
        this.inputGuardrails.clear();
        this.inputGuardrails.addAll(Arrays.asList(ensureNotNull(inputGuardrails, "inputGuardrails")));
    }

    public void outputGuardrails(OutputGuardrail... outputGuardrails) {
        this.outputGuardrails.clear();
        this.outputGuardrails.addAll(Arrays.asList(ensureNotNull(outputGuardrails, "outputGuardrails")));
    }
}
