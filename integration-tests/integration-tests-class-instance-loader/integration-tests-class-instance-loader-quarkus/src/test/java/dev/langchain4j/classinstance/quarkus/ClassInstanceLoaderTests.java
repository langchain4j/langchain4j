package dev.langchain4j.classinstance.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.example.CDIClassInstanceFactory;
import com.example.Class1;
import com.example.Class2;
import com.example.Class3;
import dev.langchain4j.classinstance.ClassInstanceLoader;
import dev.langchain4j.spi.classloading.ClassInstanceFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.CDI;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ClassInstanceLoaderTests {
    @Test
    void serviceLoaderFindsCorrectFactory() {
        assertThat(ServiceLoader.load(ClassInstanceFactory.class).findFirst())
                .get()
                .isInstanceOf(CDIClassInstanceFactory.class);
    }

    @Test
    void loadsClassInstances() {
        var instance1 = ClassInstanceLoader.getClassInstance(Class1.class);
        var instance2 = ClassInstanceLoader.getClassInstance(Class1.class);
        var instance3 = ClassInstanceLoader.getClassInstance(Class2.class);

        assertThat(instance1).isNotNull().isInstanceOf(Class1.class);
        assertThat(instance2)
                .isNotNull()
                .isInstanceOf(Class1.class)
                .isEqualTo(instance1)
                .isEqualTo(CDI.current().select(Class1.class).get());
        assertThat(instance3).isNotNull().isInstanceOf(Class2.class);
    }

    @Test
    void correctServiceLoader() {
        assertThatExceptionOfType(UnsatisfiedResolutionException.class)
                .isThrownBy(() -> ClassInstanceLoader.getClassInstance(Class3.class))
                .withMessage("No bean found for required type [class %s] and qualifiers [[]]", Class3.class.getName());
    }
}
