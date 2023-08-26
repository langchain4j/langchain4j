package dev.langchain4j.agent.tool.lambda;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import static dev.langchain4j.agent.tool.lambda.LambdaMetaUtils.getInstantiatedMethod;
import static java.lang.invoke.MethodType.fromMethodDescriptorString;

class ShadowLambdaMeta implements LambdaMeta {
    private final Class<?> implClass;
    private final Method instantiedMethod;

    public ShadowLambdaMeta(boolean reverseCompanionParameter, SerializedLambda lambda) {
        this.implClass = lambda.getCapturingClass();
        MethodType methodType = fromMethodDescriptorString(lambda.getInstantiatedMethodType(), implClass.getClassLoader());
        if (!reverseCompanionParameter) methodType = methodType.dropParameterTypes(0, 1);

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
}