package dev.langchain4j.agentic.internal;

import dev.langchain4j.Internal;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * A helper method of a declarative agent, with the object it is invoked on: a static method of the agent type, whose
 * target is {@code null}, or an instance method of a helper object.
 *
 * @param method the annotated method
 * @param target the helper object declaring the method, or {@code null} for a static method
 */
@Internal
public record HelperMethod(Method method, Object target) {

    public static HelperMethod ofStatic(Method method) {
        return new HelperMethod(method, null);
    }

    public boolean isStatic() {
        return Modifier.isStatic(method.getModifiers());
    }

    /**
     * Invokes the method on its target with the given arguments. Reflective failures, including the exception thrown
     * by the method itself, are reported wrapped in a {@link RuntimeException}.
     */
    public <T> T invoke(Object... args) {
        try {
            return (T) method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return a description of the method for error messages, with the class of its helper object when it has one
     */
    public String describe() {
        return isStatic() || target == null
                ? "the static method " + method
                : "the method " + method + " of the helper object of type "
                        + target.getClass().getName();
    }
}
