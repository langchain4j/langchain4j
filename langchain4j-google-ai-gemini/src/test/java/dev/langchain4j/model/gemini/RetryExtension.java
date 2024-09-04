package dev.langchain4j.model.gemini;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;

import java.util.concurrent.atomic.AtomicInteger;

public class RetryExtension implements TestExecutionExceptionHandler {

    private final AtomicInteger counter = new AtomicInteger(1);

    private void printError(Throwable e) {
        System.err.println("Attempt test execution #" + counter.get() +
            " failed (" + e.getClass().getName() +
            "thrown):  " + e.getMessage());
    }

    @Override
    public void handleTestExecutionException(ExtensionContext extensionContext, Throwable throwable) throws Throwable {
        printError(throwable);

        extensionContext.getTestMethod().ifPresent(method -> {
            int maxExecutions = method.getAnnotation(Retry.class) != null ?
                method.getAnnotation(Retry.class).value() : 1;

            while (counter.incrementAndGet() <= maxExecutions) {
                try {
                    extensionContext.getExecutableInvoker()
                        .invoke(method, extensionContext.getRequiredTestInstance());
                    return;
                } catch (Throwable t) {
                    printError(t);

                    if (counter.get() >= maxExecutions) {
                        throw t;
                    }
                }
            }
        });
    }
}
