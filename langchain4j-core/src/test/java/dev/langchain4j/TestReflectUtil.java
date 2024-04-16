package dev.langchain4j;


import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class TestReflectUtil {
    public static Method getMethodByName(Class<?> clazz, String methodName) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Method not found: " + methodName);
    }

    /**
     * Type trait for generic type.
     *
     * @param <T> the generic type to be traited
     * @see <a href="com/fasterxml/jackson/core/type/TypeReference">TypeReference</a>
     */
    public abstract static class TypeTrait<T> {
        protected final Type _type;

        public TypeTrait() {
            Type superClass = getClass().getGenericSuperclass();
            _type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
        }

        public Type getType() {
            return _type;
        }
    }
}
