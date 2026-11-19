package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.guardrail.config.InputGuardrailsConfig;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import dev.langchain4j.data.message.AiMessage;

/**
 * The builder hierarchy mirrors the service hierarchy: the builder of AI service guardrails is a
 * {@link GenericGuardrailService.Builder}, and its setters chain on the builder itself.
 */
class GuardrailServiceBuilderHierarchyTest {

    @InputGuardrails(AnnotatedInput.class)
    interface MyAiService {
        String chat(String message);
    }

    public static class AnnotatedInput implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }

    public static class BuilderInput implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }

    public static class BuilderOutput implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return success();
        }
    }

    @Test
    void the_ai_service_builder_is_a_generic_builder_of_the_same_kind_of_service() {
        GenericGuardrailService.Builder<
                        InputGuardrailRequest,
                        InputGuardrailResult,
                        OutputGuardrailRequest,
                        OutputGuardrailResult,
                        GuardrailService,
                        InputGuardrail,
                        OutputGuardrail,
                        InputGuardrailsConfig,
                        OutputGuardrailsConfig,
                        GuardrailService.Builder>
                generic = GuardrailService.builder(MyAiService.class);

        assertThat(generic).isInstanceOf(GuardrailService.Builder.class);
        assertThat(generic.build()).isInstanceOf(GuardrailService.class);
    }

    @Test
    void every_setter_chains_on_the_builder_itself() {
        var builder = GuardrailService.builder(MyAiService.class);

        assertThat(builder.inputGuardrailsConfig(InputGuardrailsConfig.builder().build()))
                .isSameAs(builder);
        assertThat(builder.outputGuardrailsConfig(
                        OutputGuardrailsConfig.builder().build()))
                .isSameAs(builder);
        assertThat(builder.inputGuardrails(List.of(new BuilderInput()))).isSameAs(builder);
        assertThat(builder.inputGuardrails(new BuilderInput())).isSameAs(builder);
        assertThat(builder.outputGuardrails(List.of(new BuilderOutput()))).isSameAs(builder);
        assertThat(builder.outputGuardrails(new BuilderOutput())).isSameAs(builder);
        assertThat(builder.inputGuardrailClasses(List.of(BuilderInput.class))).isSameAs(builder);
        assertThat(builder.inputGuardrailClasses(BuilderInput.class)).isSameAs(builder);
        assertThat(builder.outputGuardrailClasses(List.of(BuilderOutput.class))).isSameAs(builder);
        assertThat(builder.outputGuardrailClasses(BuilderOutput.class)).isSameAs(builder);
    }

    @Test
    void null_guardrails_clear_what_was_set_and_return_the_builder() {
        var builder = GuardrailService.builder(MyAiService.class);
        builder.inputGuardrails(new BuilderInput());

        assertThat(builder.inputGuardrails((InputGuardrail[]) null)).isSameAs(builder);
        assertThat(builder.inputGuardrails((List<InputGuardrail>) null)).isSameAs(builder);
        assertThat(builder.outputGuardrailClasses((Class<? extends OutputGuardrail>[]) null))
                .isSameAs(builder);
    }

    @Test
    void the_generic_builder_is_sealed_and_the_builders_of_each_kind_stay_open_to_frameworks() {
        assertThat(GenericGuardrailService.Builder.class.isSealed()).isTrue();
        assertThat(GenericGuardrailService.Builder.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(
                        GuardrailService.Builder.class,
                        ToolGuardrailService.Builder.class,
                        AbstractKeyedGuardrailServiceBuilder.class);
        assertThat(GuardrailService.Builder.class.isSealed()).isFalse();
        assertThat(ToolGuardrailService.Builder.class.isSealed()).isFalse();
    }

    @Test
    void the_builder_base_is_sealed_like_the_service_base() {
        assertThat(AbstractKeyedGuardrailServiceBuilder.class.isSealed()).isTrue();
        assertThat(AbstractKeyedGuardrailServiceBuilder.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(GuardrailServiceBuilder.class, ToolGuardrailServiceBuilder.class);
    }
}
