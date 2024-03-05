package dev.langchain4j.spi;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;

class ServiceHelperTest implements WithAssertions {

    private void assertServices(Collection<ExampleService> services) {
        assertThat(services).extracting(ExampleService::getGreeting)
                .containsExactlyInAnyOrder("Hello", "Goodbye");
    }

    @SuppressWarnings("unused")
    interface NotAService {
        int unused();
    }

    @Test
    public void test_loadFactories() {
        assertServices(ServiceHelper.loadFactories(ExampleService.class));
        assertServices(ServiceHelper.loadFactories(ExampleService.class, ServiceHelperTest.class.getClassLoader()));

        assertThat(ServiceHelper.loadFactories(NotAService.class)).isEmpty();
    }
}