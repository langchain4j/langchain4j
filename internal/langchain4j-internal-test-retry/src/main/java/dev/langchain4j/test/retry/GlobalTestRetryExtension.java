package dev.langchain4j.test.retry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * JUnit extension that retries tests multiple times (3 by default) in case of exceptions.
 * Disabled by default to not mess with other interceptors that might be declared in projects that import LC4j (e.g., Quarkus).
 * To enable, set the "LC4J_GLOBAL_TEST_RETRY_ENABLED" environment variable to "true"
 * and "-Djunit.jupiter.extensions.autodetection.enabled=true".
 * <p>
 * JUnit runs the {@link BeforeEach} and {@link AfterEach} methods only once, around the whole retry loop.
 * Therefore, before each retry, this extension re-runs them itself, so that every attempt starts
 * with the same state as the first one. Without it, tests that mutate an external resource
 * (e.g., integration tests that write into a database) would accumulate the data of the failed attempts.
 */
public class GlobalTestRetryExtension implements InvocationInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalTestRetryExtension.class);

    private static final boolean ENABLED = "true".equals(System.getenv("LC4J_GLOBAL_TEST_RETRY_ENABLED"));
    private static final int MAX_ATTEMPTS = Integer.parseInt(getOrDefault(System.getenv("LC4J_GLOBAL_TEST_RETRY_MAX_ATTEMPTS"), "3"));

    static {
        if (ENABLED) {
            LOG.info("{} is ACTIVE (max attempts: {})", GlobalTestRetryExtension.class.getName(), MAX_ATTEMPTS);
        } else {
            LOG.info("{} is REGISTERED but DISABLED (set LC4J_GLOBAL_TEST_RETRY_ENABLED=true to enable)", GlobalTestRetryExtension.class.getName());
        }
    }

    @Override
    public <T> T interceptTestClassConstructor(Invocation<T> invocation, ReflectiveInvocationContext<Constructor<T>> invocationContext, ExtensionContext extensionContext) throws Throwable {
        if (!ENABLED) {
            return invocation.proceed();
        }

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
                LOG.warn("Attempt {}/{} for creating an instance of {} ({}) failed because of",
                        attempt, MAX_ATTEMPTS,
                        testConstructor.getDeclaringClass().getName(), extensionContext.getDisplayName(),
                        lastThrowable);
                Thread.sleep(attempt * 5000L);
            }
        } while (attempt < MAX_ATTEMPTS);

        throw lastThrowable;
    }

    @Override
    public void interceptBeforeEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
        if (!ENABLED) {
            invocation.proceed();
            return;
        }

        executeWithRetry(invocation, invocationContext, extensionContext, false);
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
                                    ReflectiveInvocationContext<Method> invocationContext,
                                    ExtensionContext extensionContext) throws Throwable {
        if (!ENABLED) {
            invocation.proceed();
            return;
        }

        executeWithRetry(invocation, invocationContext, extensionContext, true);
    }

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                            ReflectiveInvocationContext<Method> invocationContext,
                                            ExtensionContext extensionContext) throws Throwable {
        if (!ENABLED) {
            invocation.proceed();
            return;
        }

        executeWithRetry(invocation, invocationContext, extensionContext, true);
    }

    private static void executeWithRetry(Invocation<Void> invocation,
                                         ReflectiveInvocationContext<Method> invocationContext,
                                         ExtensionContext extensionContext,
                                         boolean resetStateBeforeRetry) throws Throwable {

        Method testMethod = invocationContext.getExecutable();
        Object testObject = invocationContext.getTarget().orElseThrow();
        Object[] arguments = invocationContext.getArguments().toArray(new Object[0]);

        int attempt = 0;
        Throwable lastThrowable;

        do {
            try {
                if (attempt > 0 && resetStateBeforeRetry) {
                    resetState(testObject);
                }
                testMethod.setAccessible(true);
                testMethod.invoke(testObject, arguments);
                invocation.skip(); // to avoid failing because invocation.proceed() was not called
                return;
            } catch (Throwable t) {
                lastThrowable = getActualCause(t);
                attempt++;
                LOG.warn("Attempt {}/{} for test {}.{} ({}) failed because of",
                        attempt, MAX_ATTEMPTS,
                        testObject.getClass().getName(), testMethod.getName(), extensionContext.getDisplayName(),
                        lastThrowable);
                Thread.sleep(attempt * 5000L);
            }
        } while (attempt < MAX_ATTEMPTS);

        throw lastThrowable;
    }

    /**
     * Re-runs the {@link AfterEach} and then the {@link BeforeEach} methods of the test class,
     * bringing the test into the same state it was in before the failed attempt.
     */
    private static void resetState(Object testObject) throws Throwable {

        List<Method> afterEachMethods = findLifecycleMethods(testObject.getClass(), AfterEach.class);
        Collections.reverse(afterEachMethods); // JUnit runs @AfterEach methods bottom-up

        List<Method> methods = new ArrayList<>(afterEachMethods);
        methods.addAll(findLifecycleMethods(testObject.getClass(), BeforeEach.class));

        for (Method method : methods) {
            if (method.getParameterCount() > 0) {
                // arguments of such methods are provided by ParameterResolvers, which are not available here
                LOG.warn("Cannot reset the state of {} before retrying, because {} expects arguments. "
                                + "The test will be retried with the state left by the previous attempt.",
                        testObject.getClass().getName(), method);
                return;
            }
        }

        for (Method method : methods) {
            method.setAccessible(true);
            try {
                method.invoke(testObject);
            } catch (Throwable t) {
                throw getActualCause(t);
            }
        }
    }

    /**
     * Finds all methods annotated with the given annotation, starting from the superclasses,
     * in the same order as JUnit runs {@link BeforeEach} methods.
     */
    private static List<Method> findLifecycleMethods(Class<?> testClass, Class<? extends Annotation> annotation) {

        List<Class<?>> classes = new ArrayList<>();
        for (Class<?> clazz = testClass; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            classes.add(0, clazz);
        }

        List<Method> methods = new ArrayList<>();
        for (Class<?> clazz : classes) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(annotation)) {
                    methods.removeIf(inheritedMethod -> isOverriddenBy(inheritedMethod, method));
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    private static boolean isOverriddenBy(Method method, Method candidate) {
        return method.getName().equals(candidate.getName())
                && Arrays.equals(method.getParameterTypes(), candidate.getParameterTypes())
                && !Modifier.isPrivate(method.getModifiers())
                && !Modifier.isPrivate(candidate.getModifiers());
    }

    private static Throwable getActualCause(Throwable t) {
        if (t instanceof InvocationTargetException ite && ite.getCause() != null) {
            return t.getCause();
        } else {
            return t;
        }
    }

    private static String getOrDefault(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }
}
