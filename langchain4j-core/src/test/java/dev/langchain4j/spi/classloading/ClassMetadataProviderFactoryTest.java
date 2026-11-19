package dev.langchain4j.spi.classloading;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClassMetadataProviderFactoryTest {

    /** A factory that does not override {@code resolveMethodKey}, like one written before it existed. */
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
    void by_default_a_method_has_no_key_so_callers_fall_back_to_the_method_itself() throws Exception {
        var method = Object.class.getMethod("toString");

        assertThat(new MethodKeyedFactory().resolveMethodKey(method)).isNull();
    }
}
