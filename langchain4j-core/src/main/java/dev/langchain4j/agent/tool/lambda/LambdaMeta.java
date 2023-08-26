package dev.langchain4j.agent.tool.lambda;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static dev.langchain4j.agent.tool.lambda.LambdaMetaUtils.setAccessible;

public interface LambdaMeta {
    Class<?> getImplClass();
    Method getImplMethod();

    static LambdaMeta extract(ToolSerializedFunction func) {
        final boolean reverseCompanionParameter = func instanceof ToolSerializedCompanionFunction;
        // idea evaluate mode using proxy
        if (func instanceof Proxy) {
            return new IdeaProxyLambdaMeta(reverseCompanionParameter, (Proxy) func);
        }
        try {
            Method method = func.getClass().getDeclaredMethod("writeReplace");
            return new ReflectLambdaMeta(reverseCompanionParameter, (java.lang.invoke.SerializedLambda) setAccessible(method).invoke(func));
        } catch (Throwable e) {
            return new ShadowLambdaMeta(reverseCompanionParameter, SerializedLambda.extract(func));
        }
    }


    default Class<?> toClassConfident(String clazzName, ClassLoader classLoader) {
        try {
            return loadClass(clazzName, getClassLoaders(classLoader));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    default Class<?> loadClass(String clazzName, ClassLoader[] classLoaders) throws ClassNotFoundException {
        clazzName = clazzName.replace("/", ".");
        for (ClassLoader classLoader : classLoaders) {
            try {
                return Class.forName(clazzName, true, classLoader);
            } catch (ClassNotFoundException ignored) {}
        }

        throw new ClassNotFoundException("No class found, " + clazzName);
    }

    default ClassLoader[] getClassLoaders(ClassLoader cur){
        return new ClassLoader[]{
                cur,
                Thread.currentThread().getContextClassLoader(),
                LambdaMeta.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
    }
}