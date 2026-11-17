package dev.langchain4j.spi.classloading;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClassMetadataProviderFactoryTest {

    /** A factory whose method keys are {@link Method}s, relying on the default {@code resolveMethodKey}. */
    private static final class MethodKeyedFactory implements ClassMetadataProviderFactory<Method> {

        @Override
        public <T extends Annotation> Optional<T> getAnnotation(Method method, Class<T> annotationClass) {
            return Optional.ofNullable(method.getAnnotation(annotationClass));
        }

        @Override
        public <T extends Annotation> Optional<T> getAnnotation(Class<?> clazz, Class<T> annotationClass) {
            return Optional.ofNullable(clazz.getAnnotation(annotationClass));
        }

        @Override
        public Iterable<Method> getNonStaticMethodsOnClass(Class<?> clazz) {
            return List.of(clazz.getMethods());
        }
    }

    @Test
    void the_default_method_key_of_a_method_is_the_method_itself() throws Exception {
        var method = Object.class.getMethod("toString");

        assertThat(new MethodKeyedFactory().resolveMethodKey(method)).isSameAs(method);
    }

    @Test
    void the_default_method_key_of_null_is_null() {
        assertThat(new MethodKeyedFactory().resolveMethodKey(null)).isNull();
    }
}
