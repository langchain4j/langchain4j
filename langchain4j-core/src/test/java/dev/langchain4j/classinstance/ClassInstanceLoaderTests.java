package dev.langchain4j.classinstance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClassInstanceLoaderTests {
    @Test
    void loadsClassInstance() {
        var instance1 = ClassInstanceLoader.getClassInstance(SomeClass.class);
        var instance2 = ClassInstanceLoader.getClassInstance(SomeClass.class);

        assertThat(instance1).isNotNull().isExactlyInstanceOf(SomeClass.class);
        assertThat(instance2).isNotNull().isExactlyInstanceOf(SomeClass.class).isNotEqualTo(instance1);
    }

    public static class SomeClass {}
}
