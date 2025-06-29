package dev.langchain4j.classinstance.generic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.example.classloading.Classes;
import com.example.classloading.GenericClassInstanceFactory;
import dev.langchain4j.classinstance.ClassInstanceLoader;
import dev.langchain4j.spi.classloading.ClassInstanceFactory;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class ClassInstanceLoaderTests {
    @Test
    void serviceLoaderFindsCorrectFactory() {
        assertThat(ServiceLoader.load(ClassInstanceFactory.class).findFirst())
                .get()
                .isInstanceOf(GenericClassInstanceFactory.class);
    }

    @Test
    void loadsClassInstances() {
        var instance1 = ClassInstanceLoader.getClassInstance(Classes.Class1.class);
        var instance2 = ClassInstanceLoader.getClassInstance(Classes.Class1.class);
        var instance3 = ClassInstanceLoader.getClassInstance(Classes.Class2.class);

        assertThat(instance1).isNotNull().isExactlyInstanceOf(Classes.Class1.class);
        assertThat(instance2)
                .isNotNull()
                .isExactlyInstanceOf(Classes.Class1.class)
                .isNotEqualTo(instance1);
        assertThat(instance3).isNotNull().isExactlyInstanceOf(Classes.Class2.class);
    }

    @Test
    void correctServiceLoader() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ClassInstanceLoader.getClassInstance(Classes.Class3.class))
                .withMessage("Unknown class: %s", Classes.Class3.class.getName());
    }
}
