package dev.langchain4j.spi;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;

class ServiceHelperTest implements WithAssertions {
    public void assertServices(Collection<ExampleService> services) {
        assertThat(services).extracting(ExampleService::getGreeting)
                .containsExactlyInAnyOrder("Hello", "Goodbye");
    }

    @SuppressWarnings("unused")
    interface ServiceWithNoProviders {
        String build();
    }

    public static class ExampleServiceWithNoProviders implements ServiceWithNoProviders {
        public String build() {
            return "Hello";
        }
    }

    @Test
    public void test_loadService() {
        {
            // Existing Service.
            ExampleService service = ServiceHelper.loadService(ExampleService.class, () -> () -> "Holla");
            assertThat(service).isInstanceOfAny(ExampleServiceHello.class, ExampleServiceGoodbye.class);

            assertThat(ServiceHelper.loadFactoryService(ExampleService.class, ExampleService::getGreeting, () -> "Holla"))
                    .isIn("Hello", "Goodbye");
        }

        {
            // Fall back to default.
            ServiceWithNoProviders service = ServiceHelper.loadService(ServiceWithNoProviders.class,
                    ExampleServiceWithNoProviders::new);
            assertThat(service).isInstanceOf(ExampleServiceWithNoProviders.class);

            assertThat(ServiceHelper.loadFactoryService(ServiceWithNoProviders.class, ServiceWithNoProviders::build, () -> "Holla"))
                    .isEqualTo("Holla");
        }

    }

    @Test
    public void test_loadFactories() {
        assertServices(ServiceHelper.loadFactories(ExampleService.class));
        assertServices(ServiceHelper.loadFactories(ExampleService.class, ServiceHelperTest.class.getClassLoader()));

        assertThat(ServiceHelper.loadFactories(ServiceWithNoProviders.class)).isEmpty();
    }

    @Test
    void test_supplierServices() {
        assertThat(ServiceHelper.loadFactoryService(SupplierService.class, () -> "Not found"))
                .isEqualTo("Hello world!");

        assertThat(ServiceHelper.loadFactoryService(SupplierServiceNotFound.class, () -> "Not found"))
                .isEqualTo("Not found");
    }
}