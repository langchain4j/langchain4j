package dev.langchain4j.classinstance.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.Application;
import com.example.classes.Class1;
import com.example.classes.SpringClassMetadataProviderFactory;
import dev.langchain4j.Experimental;
import dev.langchain4j.classloading.ClassMetadataProvider;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = Application.class)
class ClassMetadataProviderTests {
    @Test
    void serviceLoaderFindsCorrectFactory() {
        assertThat(ClassMetadataProvider.getClassMetadataProviderFactory())
                .isInstanceOf(SpringClassMetadataProviderFactory.class);
    }

    @Test
    void loadsThingsCorrectly() {
        var factory = ClassMetadataProvider.<Method>getClassMetadataProviderFactory();

        assertThat(factory.getAnnotation(Class1.class, Experimental.class))
                .get()
                .extracting(Experimental::value)
                .isEqualTo("This is plain and boring!");

        assertThat(factory.getAnnotation(Class1.class, Target.class)).isEmpty();

        var methods = factory.getNonStaticMethodsOnClass(Class1.class);

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
}
