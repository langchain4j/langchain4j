package dev.langchain4j.agent.tool.lambda;

import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static dev.langchain4j.agent.tool.lambda.LambdaMetaUtils.getInstantiatedMethod;
import static dev.langchain4j.agent.tool.lambda.LambdaMetaUtils.setAccessible;

/**
 * Support for IDEA Evaluate Lambda expression
 */
class IdeaProxyLambdaMeta implements LambdaMeta {
    private static final Field FIELD_MEMBER_NAME;
    private static final Field FIELD_MEMBER_NAME_CLAZZ;
    private static final Field FIELD_MEMBER_NAME_NAME;
    private static final Method METHOD_METHOD_TYPE;

    static {
        try {
            Class<?> classDirectMethodHandle = Class.forName("java.lang.invoke.DirectMethodHandle");
            FIELD_MEMBER_NAME = setAccessible(classDirectMethodHandle.getDeclaredField("member"));
            Class<?> classMemberName = Class.forName("java.lang.invoke.MemberName");
            FIELD_MEMBER_NAME_CLAZZ = setAccessible(classMemberName.getDeclaredField("clazz"));
            FIELD_MEMBER_NAME_NAME = setAccessible(classMemberName.getDeclaredField("name"));
            METHOD_METHOD_TYPE = setAccessible(classMemberName.getDeclaredMethod("getMethodOrFieldType"));
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private final Class<?> implClass;
    private final String implMethodName;
    private final Method instantiedMethod;

    public IdeaProxyLambdaMeta(boolean reverseCompanionParameter, Proxy func) {
        InvocationHandler handler = Proxy.getInvocationHandler(func);
        try {
            Object dmh = setAccessible(handler.getClass().getDeclaredField("val$target")).get(handler);
            Object member = FIELD_MEMBER_NAME.get(dmh);
            MethodType methodType = (MethodType) METHOD_METHOD_TYPE.invoke(member);
            if (!reverseCompanionParameter) methodType = methodType.dropParameterTypes(0, 1);

            this.implClass = (Class<?>) FIELD_MEMBER_NAME_CLAZZ.get(member);
            this.implMethodName = (String) FIELD_MEMBER_NAME_NAME.get(member);
            this.instantiedMethod = getInstantiatedMethod(methodType, implClass, implMethodName);
        } catch (IllegalAccessException | NoSuchFieldException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
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
        return implClass.getSimpleName() + "::" + implMethodName;
    }

}