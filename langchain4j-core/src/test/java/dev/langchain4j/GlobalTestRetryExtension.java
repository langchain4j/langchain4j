package dev.langchain4j;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class GlobalTestRetryExtension implements InvocationInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalTestRetryExtension.class);
    private static final int MAX_ATTEMPTS = 3;

    @Override
    public <T> T interceptTestClassConstructor(Invocation<T> invocation, ReflectiveInvocationContext<Constructor<T>> invocationContext, ExtensionContext extensionContext) throws Throwable {
        Constructor<T> testConstructor = invocationContext.getExecutable();
        Object[] arguments = invocationContext.getArguments().toArray(new Object[0]);

        int attempt = 0;
        Throwable lastThrowable;

        do {
            try {
                testConstructor.setAccessible(true);
                T testObject = testConstructor.newInstance(arguments);
                invocation.skip(); // to avoid failing because invocation.proceed() was not called
                return testObject;
            } catch (Throwable t) {
                lastThrowable = getActualCause(t);
                attempt++;
                LOG.warn("Attempt {}/{} for creating an instance of '{}' failed because of",
                        attempt, MAX_ATTEMPTS, extensionContext.getDisplayName(), lastThrowable);
                Thread.sleep(attempt * 1000L);
            }
        } while (attempt < MAX_ATTEMPTS);

        throw lastThrowable;
    }

    @Override
    public void interceptBeforeEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
        executeWithRetry(invocation, invocationContext, extensionContext);
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
                                    ReflectiveInvocationContext<Method> invocationContext,
                                    ExtensionContext extensionContext) throws Throwable {
        executeWithRetry(invocation, invocationContext, extensionContext);
    }

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                            ReflectiveInvocationContext<Method> invocationContext,
                                            ExtensionContext extensionContext) throws Throwable {
        executeWithRetry(invocation, invocationContext, extensionContext);
    }

    private static void executeWithRetry(Invocation<Void> invocation,
                                         ReflectiveInvocationContext<Method> invocationContext,
                                         ExtensionContext extensionContext) throws Throwable {

        Method testMethod = invocationContext.getExecutable();
        Object testObject = invocationContext.getTarget().orElseThrow();
        Object[] arguments = invocationContext.getArguments().toArray(new Object[0]);

        int attempt = 0;
        Throwable lastThrowable;

        do {
            try {
                testMethod.setAccessible(true);
                testMethod.invoke(testObject, arguments);
                invocation.skip(); // to avoid failing because invocation.proceed() was not called
                return;
            } catch (Throwable t) {
                lastThrowable = getActualCause(t);
                attempt++;
                LOG.warn("Attempt {}/{} for test '{}' failed because of",
                        attempt, MAX_ATTEMPTS, extensionContext.getDisplayName(), lastThrowable);
                Thread.sleep(attempt * 1000L);
            }
        } while (attempt < MAX_ATTEMPTS);

        throw lastThrowable;
    }

    private static Throwable getActualCause(Throwable t) {
        if (t instanceof InvocationTargetException ite && ite.getCause() != null) {
            return t.getCause();
        } else {
            return t;
        }
    }
}
