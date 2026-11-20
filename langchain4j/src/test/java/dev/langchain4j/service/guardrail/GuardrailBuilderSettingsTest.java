package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.config.InputGuardrailsConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuardrailBuilderSettingsTest {

    public static class First implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }

    public static class Second implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }

    private static GuardrailBuilderSettings<InputGuardrail, InputGuardrailsConfig> settings() {
        return new GuardrailBuilderSettings<>();
    }

    @Test
    void nothing_is_set_by_default() {
        var settings = settings();

        assertThat(settings.hasConfig()).isFalse();
        assertThat(settings.hasGuardrails()).isFalse();
        assertThat(settings.hasGuardrailsAndConfig()).isFalse();
        assertThat(settings.config()).isEmpty();
        assertThat(settings.resolveGuardrails()).isEmpty();
    }

    @Test
    void the_resolved_guardrails_are_the_instances_followed_by_the_ones_created_from_the_classes() {
        var instance = new Second();
        var settings = settings().guardrailClasses(List.of(First.class)).guardrails(List.of(instance));

        assertThat(settings.hasGuardrails()).isTrue();
        assertThat(settings.resolveGuardrails())
                .hasSize(2)
                .satisfies(guardrails -> assertThat(guardrails.get(0)).isSameAs(instance))
                .satisfies(guardrails -> assertThat(guardrails.get(1)).isExactlyInstanceOf(First.class));
    }

    @Test
    void classes_are_instantiated_each_time_the_guardrails_are_resolved() {
        var settings = settings().guardrailClasses(List.of(First.class));

        assertThat(settings.resolveGuardrails().get(0))
                .isNotSameAs(settings.resolveGuardrails().get(0));
    }

    @Test
    void setting_guardrails_or_classes_replaces_what_was_set_before() {
        var settings = settings()
                .guardrails(List.of(new First()))
                .guardrailClasses(List.of(First.class))
                .guardrails(List.of(new Second()))
                .guardrailClasses(List.of(Second.class));

        assertThat(settings.resolveGuardrails())
                .extracting(Object::getClass)
                .containsExactly(Second.class, Second.class);
    }

    @Test
    void null_clears_the_guardrails_and_the_classes() {
        var settings = settings()
                .guardrails(List.of(new First()))
                .guardrailClasses(List.of(First.class))
                .guardrails(null)
                .guardrailClasses(null);

        assertThat(settings.hasGuardrails()).isFalse();
        assertThat(settings.resolveGuardrails()).isEmpty();
    }

    @Test
    void the_config_is_kept_and_must_not_be_null() {
        var config = InputGuardrailsConfig.builder().build();
        var settings = settings().config(config);

        assertThat(settings.hasConfig()).isTrue();
        assertThat(settings.config()).containsSame(config);
        assertThat(settings.hasGuardrailsAndConfig()).isFalse();
        assertThat(settings.guardrails(List.of(new First())).hasGuardrailsAndConfig())
                .isTrue();
        assertThatThrownBy(() -> settings().config(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_class_is_enough_to_have_guardrails() {
        assertThat(settings().guardrailClasses(List.of(First.class)).hasGuardrails())
                .isTrue();
    }

    @Test
    void instantiate_creates_a_guardrail_and_rejects_null() {
        assertThat(GuardrailBuilderSettings.<InputGuardrail>instantiate(First.class))
                .isExactlyInstanceOf(First.class);
        assertThatThrownBy(() -> GuardrailBuilderSettings.instantiate(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void instantiate_all_creates_the_guardrails_in_order_and_rejects_null() {
        Class<? extends InputGuardrail>[] classes = new Class[] {Second.class, First.class};

        assertThat(GuardrailBuilderSettings.<InputGuardrail>instantiateAll(classes))
                .extracting(Object::getClass)
                .containsExactly(Second.class, First.class);
        assertThatThrownBy(() -> GuardrailBuilderSettings.instantiateAll(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
