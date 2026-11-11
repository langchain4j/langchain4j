package dev.langchain4j.spi;

import java.util.Collection;
import java.util.List;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ServiceHelperTest implements WithAssertions {

    private void assertServices(Collection<ExampleService> services) {
        assertThat(services).extracting(ExampleService::getGreeting).containsExactlyInAnyOrder("Hello", "Goodbye");
    }

    @SuppressWarnings("unused")
    interface NotAService {
        int unused();
    }

    @Test
    void load_factories() {
        assertServices(ServiceHelper.loadFactories(ExampleService.class));
        assertServices(ServiceHelper.loadFactories(ExampleService.class, ServiceHelperTest.class.getClassLoader()));

        assertThat(ServiceHelper.loadFactories(NotAService.class)).isEmpty();
    }

    @Test
    void load_factory() {
        assertThat(ServiceHelper.loadFactory(ExampleService.class).getGreeting()).isEqualTo("Hello");
        assertThat(ServiceHelper.ambiguityWarning(
                        ExampleService.class, List.copyOf(ServiceHelper.loadFactories(ExampleService.class))))
                .contains("using " + ExampleServiceHello.class.getName())
                .contains("ignoring [" + ExampleServiceGoodbye.class.getName() + "]");

        assertThat(ServiceHelper.loadFactory(NotAService.class)).isNull();
    }
}
