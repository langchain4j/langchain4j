package dev.langchain4j.classinstance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.Experimental;
import dev.langchain4j.classloading.ReflectionBasedClassMetadataProviderFactory;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.guardrail.InputGuardrails;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class ReflectionBasedClassMetadataProviderFactoryTests {
    ReflectionBasedClassMetadataProviderFactory factory = new ReflectionBasedClassMetadataProviderFactory();

    private Map<String, Method> getMethodsOnClass() {
        var nonStaticMethodsOnClass = StreamSupport.stream(
                        factory.getNonStaticMethodsOnClass(Assistant.class).spliterator(), false)
                .collect(Collectors.toMap(Method::getName, Function.identity()));

        assertThat(nonStaticMethodsOnClass).hasSize(2).containsOnlyKeys("hello", "helloStreaming");

        return nonStaticMethodsOnClass;
    }

    @Test
    void annotationOnClass() {
        assertThat(factory.getAnnotation(Assistant.class, Experimental.class))
                .get()
                .extracting(Experimental::value)
                .isEqualTo("This is a test");
    }

    @Test
    void annotationOnClassNotFound() {
        assertThat(factory.getAnnotation(Assistant.class, InputGuardrails.class))
                .isEmpty();
    }

    @Test
    void annotationOnMethod() {
        var nonStaticMethodsOnClass = getMethodsOnClass();

        assertThat(factory.getAnnotation(nonStaticMethodsOnClass.get("hello"), Experimental.class))
                .get()
                .extracting(Experimental::value)
                .isEqualTo("This is just a test");

        assertThat(factory.getAnnotation(nonStaticMethodsOnClass.get("helloStreaming"), Experimental.class))
                .get()
                .extracting(Experimental::value)
                .isEqualTo("This is another test");
    }

    @Test
    void annotationsOnMethodNotFound() {
        var nonStaticMethodsOnClass = getMethodsOnClass();

        assertThat(factory.getAnnotation(nonStaticMethodsOnClass.get("hello"), InputGuardrails.class))
                .isEmpty();

        assertThat(factory.getAnnotation(nonStaticMethodsOnClass.get("helloStreaming"), InputGuardrails.class))
                .isEmpty();
    }

    @Experimental("This is a test")
    interface Assistant {
        @Experimental("This is just a test")
        String hello();

        @Experimental("This is another test")
        TokenStream helloStreaming();
    }
}
