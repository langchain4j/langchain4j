package dev.langchain4j.agent.tool.lambda;

import java.lang.invoke.MethodType;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static dev.langchain4j.agent.tool.lambda.LambdaMetaUtils.*;
import static java.lang.invoke.MethodType.fromMethodDescriptorString;

class ReflectLambdaMeta implements LambdaMeta {
    private final Method instantiedMethod;
    private final Class<?> implClass;

    ReflectLambdaMeta(boolean reverseCompanionParameter, SerializedLambda lambda) {
        final ClassLoader capturingClassLoader = getCapturingClassClassLoader(lambda);
        MethodType methodType = fromMethodDescriptorString(lambda.getInstantiatedMethodType(), capturingClassLoader);
        if (!reverseCompanionParameter) methodType = methodType.dropParameterTypes(0, 1);

        this.implClass = toClassConfident(lambda.getImplClass(), capturingClassLoader);
        this.instantiedMethod = getInstantiatedMethod(methodType, implClass, lambda.getImplMethodName());
    }

    @Override
    public Class<?> getImplClass() {
        return implClass;
    }

    @Override
    public Method getImplMethod() {
        return instantiedMethod;
    }

    @Override
    public String toString() {
        return implClass.getSimpleName() + "::" + instantiedMethod.getName();
    }

    private ClassLoader getCapturingClassClassLoader(SerializedLambda lambda) {
        if (FIELD_CAPTURING_CLASS == null) {
            return null;
        }
        try {
            return ((Class<?>) FIELD_CAPTURING_CLASS.get(lambda)).getClassLoader();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Field FIELD_CAPTURING_CLASS;

    static {
        Field fieldCapturingClass;
        try {
            fieldCapturingClass = setAccessible(SerializedLambda.class.getDeclaredField("capturingClass"));
        } catch (Throwable e) {
            fieldCapturingClass = null;
        }
        FIELD_CAPTURING_CLASS = fieldCapturingClass;
    }
}