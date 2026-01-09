package dev.langchain4j.model.watsonx;

import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.parallel.ExecutionMode.*;

public class ExecutionInfoExtension
        implements BeforeEachCallback, BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        log(context, "beforeAll");
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        log(context, "beforeEach");
    }

    private void log(ExtensionContext context, String phase) {
        ExecutionMode mode = context.getElement()
                .map(el -> context.getExecutionMode())
                .orElse(SAME_THREAD);

        boolean parallelEnabled = context.getConfigurationParameter(
                "junit.jupiter.execution.parallel.enabled"
        ).map(Boolean::parseBoolean).orElse(false);

        System.out.printf(
                "[%s] %s | executionMode=%s | parallelEnabled=%s | thread=%s%n",
                phase,
                context.getDisplayName(),
                mode,
                parallelEnabled,
                Thread.currentThread().getName()
        );
    }
}
