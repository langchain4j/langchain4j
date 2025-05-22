package dev.langchain4j.classinstance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.Experimental;
import dev.langchain4j.classloading.ClassMetadataProvider;
import dev.langchain4j.classloading.ReflectionBasedClassMetadataProviderFactory;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class ClassMetadataProviderTests {
    @Test
    void loadsThingsCorrectly() {
        var factory = ClassMetadataProvider.<Method>getClassMetadataProviderFactory();

        assertThat(factory).isNotNull().isExactlyInstanceOf(ReflectionBasedClassMetadataProviderFactory.class);

        assertThat(factory.getAnnotation(SomeInterface.class, Experimental.class))
                .get()
                .extracting(Experimental::value)
                .isEqualTo("This is plain and boring!");

        assertThat(factory.getAnnotation(SomeInterface.class, Target.class)).isEmpty();

        var methods = factory.getNonStaticMethodsOnClass(SomeInterface.class);

        assertThat(methods).hasSize(2).extracting(Method::getName).containsExactlyInAnyOrder("hello", "goodbye");

        var methodsByName = StreamSupport.stream(methods.spliterator(), false)
                .collect(Collectors.toMap(Method::getName, method -> method));

        var helloMethod = methodsByName.get("hello");
        var goodbyeMethod = methodsByName.get("goodbye");

        assertThat(helloMethod).isNotNull();

        assertThat(goodbyeMethod).isNotNull();

        assertThat(factory.getAnnotation(goodbyeMethod, Experimental.class))
                .get()
                .extracting(Experimental::value)
                .isEqualTo("Just trying things out");

        assertThat(factory.getAnnotation(goodbyeMethod, Target.class)).isEmpty();

        assertThat(factory.getAnnotation(helloMethod, Experimental.class)).isEmpty();

        assertThat(factory.getAnnotation(helloMethod, Target.class)).isEmpty();
    }

    @Experimental("This is plain and boring!")
    interface SomeInterface {
        String hello();

        @Experimental("Just trying things out")
        String goodbye();

        static String wave() {
            return "wave";
        }
    }
}
