package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AbstractKeyedGuardrailServiceTest {

    @Test
    void the_generic_service_is_sealed_and_the_ai_service_interface_stays_open_to_frameworks() {
        assertThat(GenericGuardrailService.class.isSealed()).isTrue();
        assertThat(GenericGuardrailService.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(GuardrailService.class, AbstractKeyedGuardrailService.class);
        assertThat(GuardrailService.class.isSealed()).isFalse();
    }

    @Test
    void only_the_services_of_this_package_can_extend_it() {
        assertThat(AbstractKeyedGuardrailService.class.isSealed()).isTrue();
        assertThat(AbstractKeyedGuardrailService.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(AbstractGuardrailService.class, DefaultAiServiceGuardrailService.class);
    }

    @Test
    void the_deprecated_extension_point_stays_open_to_the_frameworks_that_subclass_it() {
        assertThat(AbstractGuardrailService.class.isSealed()).isFalse();
        assertThat(Modifier.isFinal(AbstractGuardrailService.class.getModifiers()))
                .isFalse();
        assertThat(Arrays.asList(AbstractGuardrailService.class.getDeclaredConstructors()))
                .anySatisfy(constructor -> assertThat(Modifier.isProtected(constructor.getModifiers()))
                        .isTrue());
    }

    @Test
    void the_default_ai_service_implementation_is_final() {
        assertThat(Modifier.isFinal(DefaultAiServiceGuardrailService.class.getModifiers()))
                .isTrue();
    }
}
