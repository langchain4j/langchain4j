package dev.langchain4j.store.embedding.vearch;

import org.junit.jupiter.api.TestInfo;

import java.lang.reflect.Method;
import java.util.Optional;

class TestUtils {

    private TestUtils() {

    }

    static boolean isMethodFromClass(TestInfo testInfo, Class<?> clazz) {
        try {
            Optional<Method> method = testInfo.getTestMethod();
            if (method.isPresent()) {
                String methodName = method.get().getName();
                return clazz.getDeclaredMethod(methodName) != null;
            }
            return false;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
