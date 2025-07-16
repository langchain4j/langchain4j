package dev.langchain4j.classinstance.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.example.Application;
import com.example.classes.ApplicationContextClassInstanceFactory;
import com.example.classes.Class1;
import com.example.classes.Class2;
import com.example.classes.Class3;
import dev.langchain4j.classinstance.ClassInstanceLoader;
import dev.langchain4j.spi.classloading.ClassInstanceFactory;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(classes = Application.class)
class ClassInstanceLoaderTests {
    @Autowired
    ApplicationContext applicationContext;

    @Test
    void serviceLoaderFindsCorrectFactory() {
        assertThat(ServiceLoader.load(ClassInstanceFactory.class).findFirst())
                .get()
                .isInstanceOf(ApplicationContextClassInstanceFactory.class);
    }

    @Test
    void loadsClassInstances() {
        var instance1 = ClassInstanceLoader.getClassInstance(Class1.class);
        var instance2 = ClassInstanceLoader.getClassInstance(Class1.class);
        var instance3 = ClassInstanceLoader.getClassInstance(Class2.class);

        assertThat(instance1).isNotNull().isExactlyInstanceOf(Class1.class);
        assertThat(instance2)
                .isNotNull()
                .isExactlyInstanceOf(Class1.class)
                .isEqualTo(instance1)
                .isEqualTo(this.applicationContext.getBean(Class1.class));
        assertThat(instance3).isNotNull().isExactlyInstanceOf(Class2.class);
    }

    @Test
    void correctServiceLoader() {
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
                .isThrownBy(() -> ClassInstanceLoader.getClassInstance(Class3.class))
                .withMessage("No qualifying bean of type '%s' available", Class3.class.getName());
    }
}
