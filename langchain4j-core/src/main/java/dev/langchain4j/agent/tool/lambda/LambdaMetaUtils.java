package dev.langchain4j.agent.tool.lambda;

import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;

interface LambdaMetaUtils {

    static <T extends AccessibleObject> T setAccessible(T object) {
        object.setAccessible(true);
        return object;
    }

    static Method getInstantiatedMethod(MethodType methodType, Class<?> implClass, String implMethodName) {
        try {
            return implClass.getDeclaredMethod(implMethodName, methodType.parameterArray());
        } catch (NoSuchMethodException e) {
            return searchMethod(implClass.getDeclaredMethods(), implMethodName, methodType.parameterArray());
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        }
    }

    static Method searchMethod(Method[] methods, String candidateMethodName, Class<?>[] candidateParameterTypes) {
        Method instantiedMethod = null;
        for (Method method : methods) {
            if (!method.getName().equals(candidateMethodName)) continue;
            if (method.getParameterCount() != candidateParameterTypes.length) continue;

            final Class<?>[] parameterTypes = method.getParameterTypes();
            if (matchMethodParams(parameterTypes, candidateParameterTypes)) {
                if (instantiedMethod != null) {
                    throw new IllegalArgumentException("Reference to '" + instantiedMethod.getName() +
                            "' is ambiguous, both '" + instantiedMethod +
                            "' and '" + method + "' match");
                }
                instantiedMethod = method;
            }
        }

        if (instantiedMethod == null) throw new IllegalArgumentException("No search found method.");
        return instantiedMethod;
    }

    static boolean matchMethodParams(Class<?>[] parameterTypes, Class<?>[] parameterization) {
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!isAssignmentCompatible(parameterTypes[i], parameterization[i])) return false;
        }

        return true;
    }

    static boolean isAssignmentCompatible(Class<?> parameterType, Class<?> parameterization) {
        if (parameterType.isAssignableFrom(parameterization)) return true;
        if (parameterType.isPrimitive()) {
            Class<?> parameterWrapperClazz = getPrimitiveWrapper(parameterType);
            if (parameterWrapperClazz != null) {
                return parameterWrapperClazz.equals(parameterization);
            }
        }

        return false;
    }

    static Class<?> getPrimitiveWrapper(Class<?> primitiveType) {
        if (Boolean.TYPE.equals(primitiveType)) {
            return Boolean.class;
        } else if (Float.TYPE.equals(primitiveType)) {
            return Float.class;
        } else if (Long.TYPE.equals(primitiveType)) {
            return Long.class;
        } else if (Integer.TYPE.equals(primitiveType)) {
            return Integer.class;
        } else if (Short.TYPE.equals(primitiveType)) {
            return Short.class;
        } else if (Byte.TYPE.equals(primitiveType)) {
            return Byte.class;
        } else if (Double.TYPE.equals(primitiveType)) {
            return Double.class;
        } else {
            return Character.TYPE.equals(primitiveType) ? Character.class : null;
        }
    }
}
