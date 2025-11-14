package dev.langchain4j;

import static dev.langchain4j.internal.RetryUtils.withRetry;

import java.lang.reflect.Method;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

public class GlobalTestRetryExtension implements InvocationInterceptor {

    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
                                    ReflectiveInvocationContext<Method> invocationContext,
                                    ExtensionContext extensionContext) throws Throwable {
        executeTestWithRetry(invocation, invocationContext, extensionContext);
    }

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                            ReflectiveInvocationContext<Method> invocationContext,
                                            ExtensionContext extensionContext) throws Throwable {
        executeTestWithRetry(invocation, invocationContext, extensionContext);
    }

    private static void executeTestWithRetry(Invocation<Void> invocation,
                                             ReflectiveInvocationContext<Method> invocationContext,
                                             ExtensionContext extensionContext) throws Throwable {

        Method testMethod = invocationContext.getExecutable();
        Object testObject = invocationContext.getTarget().orElseThrow();
        Object[] arguments = invocationContext.getArguments().toArray(new Object[0]);

        withRetry(() -> {
            testMethod.setAccessible(true);
            testMethod.invoke(testObject, arguments);
            invocation.skip(); // to avoid failing because invocation.proceed() was not called
            return null;
        });
    }
}
