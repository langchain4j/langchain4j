package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.guardrail.config.InputGuardrailsConfig;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultGuardrailServiceTests {
    @Test
    void noGuardrails() {
        var guardrailService = (DefaultGuardrailService)
                GuardrailService.builder(NoGuardrailAssistant.class).build();

        assertThat(guardrailService.getInputGuardrailMethodCount()).isEqualTo(0);
        assertThat(guardrailService.getOutputGuardrailMethodCount()).isEqualTo(0);
        assertThat(guardrailService.getInputConfig("chat")).isEmpty();
        assertThat(guardrailService.getInputGuardrails("chat")).isEmpty();
        assertThat(guardrailService.getOutputConfig("chat")).isEmpty();
        assertThat(guardrailService.getOutputGuardrails("chat")).isEmpty();
        assertThat(guardrailService.getInputConfig("chat2")).isEmpty();
        assertThat(guardrailService.getInputGuardrails("chat2")).isEmpty();
        assertThat(guardrailService.getOutputConfig("chat2")).isEmpty();
        assertThat(guardrailService.getOutputGuardrails("chat2")).isEmpty();
    }

    @Test
    void classLevelGuardrailsNoBuilders() {
        var gs = GuardrailService.builder(ClassLevelAssistant.class).build();
        assertThat(gs).isInstanceOf(DefaultGuardrailService.class);
        var guardrailService = (DefaultGuardrailService) gs;

        assertThat(guardrailService.getInputGuardrailMethodCount()).isEqualTo(2);
        assertThat(guardrailService.getOutputGuardrailMethodCount()).isEqualTo(2);
        assertThat(guardrailService.getInputConfig("chat")).isNotEmpty();

        assertThat(guardrailService.getInputGuardrails("chat")).singleElement().isExactlyInstanceOf(IG1.class);

        assertThat(guardrailService.getOutputConfig("chat"))
                .get()
                .extracting(dev.langchain4j.guardrail.config.OutputGuardrailsConfig::maxRetries)
                .isEqualTo(dev.langchain4j.guardrail.config.OutputGuardrailsConfig.MAX_RETRIES_DEFAULT);

        assertThat(guardrailService.getOutputGuardrails("chat")).singleElement().isExactlyInstanceOf(OG1.class);

        assertThat(guardrailService.getInputConfig("chat2")).isNotEmpty();

        assertThat(guardrailService.getInputGuardrails("chat2")).singleElement().isExactlyInstanceOf(IG1.class);

        assertThat(guardrailService.getOutputConfig("chat2"))
                .get()
                .extracting(dev.langchain4j.guardrail.config.OutputGuardrailsConfig::maxRetries)
                .isEqualTo(dev.langchain4j.guardrail.config.OutputGuardrailsConfig.MAX_RETRIES_DEFAULT);

        assertThat(guardrailService.getOutputGuardrails("chat2"))
                .singleElement()
                .isExactlyInstanceOf(OG1.class);
    }

    @ParameterizedTest
    @MethodSource("classLevelGuardrailBuilders")
    void classLevelGuardrails(String testDescription, GuardrailService gs) {
        assertThat(gs).isInstanceOf(DefaultGuardrailService.class);
        var guardrailService = (DefaultGuardrailService) gs;

        assertThat(guardrailService.getInputGuardrailMethodCount()).isEqualTo(2);
        assertThat(guardrailService.getOutputGuardrailMethodCount()).isEqualTo(2);
        assertThat(guardrailService.getInputConfig("chat")).isNotEmpty();

        assertThat(guardrailService.getInputGuardrails("chat")).singleElement().isExactlyInstanceOf(IG1.class);

        assertThat(guardrailService.getOutputConfig("chat"))
                .get()
                .extracting(dev.langchain4j.guardrail.config.OutputGuardrailsConfig::maxRetries)
                .isEqualTo(dev.langchain4j.guardrail.config.OutputGuardrailsConfig.MAX_RETRIES_DEFAULT);

        assertThat(guardrailService.getOutputGuardrails("chat")).singleElement().isExactlyInstanceOf(OG1.class);

        assertThat(guardrailService.getInputConfig("chat2")).isNotEmpty();

        assertThat(guardrailService.getInputGuardrails("chat2")).singleElement().isExactlyInstanceOf(IG1.class);

        assertThat(guardrailService.getOutputConfig("chat2"))
                .get()
                .extracting(dev.langchain4j.guardrail.config.OutputGuardrailsConfig::maxRetries)
                .isEqualTo(dev.langchain4j.guardrail.config.OutputGuardrailsConfig.MAX_RETRIES_DEFAULT);

        assertThat(guardrailService.getOutputGuardrails("chat2"))
                .singleElement()
                .isExactlyInstanceOf(OG1.class);
    }

    @Test
    void methodLevelGuardrails() {
        var guardrailService = (DefaultGuardrailService)
                GuardrailService.builder(MethodLevelAssistant.class).build();

        assertThat(guardrailService.getInputGuardrailMethodCount()).isEqualTo(2);
        assertThat(guardrailService.getOutputGuardrailMethodCount()).isEqualTo(2);
        assertThat(guardrailService.getInputConfig("chat")).isNotEmpty();

        assertThat(guardrailService.getInputGuardrails("chat")).singleElement().isExactlyInstanceOf(IG1.class);

        assertThat(guardrailService.getOutputConfig("chat"))
                .get()
                .extracting(dev.langchain4j.guardrail.config.OutputGuardrailsConfig::maxRetries)
                .isEqualTo(dev.langchain4j.guardrail.config.OutputGuardrailsConfig.MAX_RETRIES_DEFAULT);

        assertThat(guardrailService.getOutputGuardrails("chat")).singleElement().isExactlyInstanceOf(OG1.class);

        assertThat(guardrailService.getInputConfig("chat2")).isNotEmpty();

        assertThat(guardrailService.getInputGuardrails("chat2")).singleElement().isExactlyInstanceOf(IG2.class);

        assertThat(guardrailService.getOutputConfig("chat2"))
                .get()
                .extracting(dev.langchain4j.guardrail.config.OutputGuardrailsConfig::maxRetries)
                .isEqualTo(dev.langchain4j.guardrail.config.OutputGuardrailsConfig.MAX_RETRIES_DEFAULT);

        assertThat(guardrailService.getOutputGuardrails("chat2"))
                .singleElement()
                .isExactlyInstanceOf(OG2.class);
    }

    @Test
    void classAndMethodLevelGuardrailsNoBuilders() {
        var gs = GuardrailService.builder(ClassAndMethodLevelAssistant.class).build();
        assertThat(gs).isInstanceOf(DefaultGuardrailService.class);
        var guardrailService = (DefaultGuardrailService) gs;

        assertThat(guardrailService.getInputGuardrailMethodCount()).isEqualTo(2);
        assertThat(guardrailService.getOutputGuardrailMethodCount()).isEqualTo(2);
        assertThat(guardrailService.getInputConfig("chat")).isNotEmpty();

        assertThat(guardrailService.getInputGuardrails("chat"))
                .hasSize(2)
                .satisfiesExactly(
                        guardrail -> assertThat(guardrail).isInstanceOf(IG1.class),
                        guardrail -> assertThat(guardrail).isInstanceOf(IG2.class));

        assertThat(guardrailService.getOutputConfig("chat"))
                .get()
                .extracting(dev.langchain4j.guardrail.config.OutputGuardrailsConfig::maxRetries)
                .isEqualTo(10);

        assertThat(guardrailService.getOutputGuardrails("chat"))
                .hasSize(2)
                .satisfiesExactly(
                        guardrail -> assertThat(guardrail).isInstanceOf(OG1.class),
                        guardrail -> assertThat(guardrail).isInstanceOf(OG2.class));

        assertThat(guardrailService.getInputConfig("chat2")).isNotEmpty();

        assertThat(guardrailService.getInputGuardrails("chat2")).singleElement().isExactlyInstanceOf(IG1.class);

        assertThat(guardrailService.getOutputConfig("chat2"))
                .get()
                .extracting(dev.langchain4j.guardrail.config.OutputGuardrailsConfig::maxRetries)
                .isEqualTo(dev.langchain4j.guardrail.config.OutputGuardrailsConfig.MAX_RETRIES_DEFAULT);

        assertThat(guardrailService.getOutputGuardrails("chat2"))
                .singleElement()
                .isExactlyInstanceOf(OG1.class);
    }

    @Test
    void builderGuardrailsTakePrecedenceOverClassAndMethodAnnotations() {
        var guardrailService = (DefaultGuardrailService) GuardrailService.builder(ClassAndMethodLevelAssistant.class)
                .inputGuardrailClasses(IG2.class)
                .outputGuardrails(new OG2())
                .build();

        assertThat(guardrailService.getInputGuardrails("chat")).singleElement().isExactlyInstanceOf(IG2.class);
        assertThat(guardrailService.getInputGuardrails("chat2")).singleElement().isExactlyInstanceOf(IG2.class);
        assertThat(guardrailService.getOutputGuardrails("chat")).singleElement().isExactlyInstanceOf(OG2.class);
        assertThat(guardrailService.getOutputGuardrails("chat2"))
                .singleElement()
                .isExactlyInstanceOf(OG2.class);

        // without a config on the builder, the config still comes from the annotation
        assertThat(guardrailService.getOutputConfig("chat"))
                .get()
                .extracting(OutputGuardrailsConfig::maxRetries)
                .isEqualTo(10);
        assertThat(guardrailService.getOutputConfig("chat2"))
                .get()
                .extracting(OutputGuardrailsConfig::maxRetries)
                .isEqualTo(OutputGuardrailsConfig.MAX_RETRIES_DEFAULT);
    }

    @Test
    void builderConfigTakesPrecedenceOverAnnotationConfig() {
        var inputGuardrailsConfig = InputGuardrailsConfig.builder().build();
        var guardrailService = (DefaultGuardrailService) GuardrailService.builder(ClassAndMethodLevelAssistant.class)
                .inputGuardrailsConfig(inputGuardrailsConfig)
                .outputGuardrailsConfig(
                        OutputGuardrailsConfig.builder().maxRetries(5).build())
                .build();

        // the guardrails still come from the annotations
        assertThat(guardrailService.getInputGuardrails("chat"))
                .satisfiesExactly(
                        guardrail -> assertThat(guardrail).isInstanceOf(IG1.class),
                        guardrail -> assertThat(guardrail).isInstanceOf(IG2.class));
        assertThat(guardrailService.getInputGuardrails("chat2"))
                .singleElement()
                .isExactlyInstanceOf(IG1.class);
        assertThat(guardrailService.getOutputGuardrails("chat"))
                .satisfiesExactly(
                        guardrail -> assertThat(guardrail).isInstanceOf(OG1.class),
                        guardrail -> assertThat(guardrail).isInstanceOf(OG2.class));
        assertThat(guardrailService.getOutputGuardrails("chat2"))
                .singleElement()
                .isExactlyInstanceOf(OG1.class);

        assertThat(guardrailService.getOutputConfig("chat"))
                .get()
                .extracting(OutputGuardrailsConfig::maxRetries)
                .isEqualTo(5);
        assertThat(guardrailService.getOutputConfig("chat2"))
                .get()
                .extracting(OutputGuardrailsConfig::maxRetries)
                .isEqualTo(5);
        assertThat(guardrailService.getInputConfig("chat")).containsSame(inputGuardrailsConfig);
        assertThat(guardrailService.getInputConfig("chat2")).containsSame(inputGuardrailsConfig);
    }

    @Test
    void builderGuardrailsAndConfigsTakePrecedenceOverClassAndMethodAnnotations() {
        var inputGuardrailsConfig = InputGuardrailsConfig.builder().build();
        var guardrailService = (DefaultGuardrailService) GuardrailService.builder(ClassAndMethodLevelAssistant.class)
                .inputGuardrails(new IG2())
                .inputGuardrailsConfig(inputGuardrailsConfig)
                .outputGuardrailClasses(OG2.class)
                .outputGuardrailsConfig(
                        OutputGuardrailsConfig.builder().maxRetries(5).build())
                .build();

        assertThat(Stream.of("chat", "chat2")).allSatisfy(method -> {
            assertThat(guardrailService.getInputGuardrails(method))
                    .singleElement()
                    .isExactlyInstanceOf(IG2.class);
            assertThat(guardrailService.getInputConfig(method)).containsSame(inputGuardrailsConfig);
            assertThat(guardrailService.getOutputGuardrails(method))
                    .singleElement()
                    .isExactlyInstanceOf(OG2.class);
            assertThat(guardrailService.getOutputConfig(method))
                    .get()
                    .extracting(OutputGuardrailsConfig::maxRetries)
                    .isEqualTo(5);
        });
    }

    @ParameterizedTest
    @MethodSource("classAndMethodLevelGuardrailBuilders")
    void classAndMethodLevelGuardrails(String testDescription, GuardrailService gs) {
        assertThat(gs).isInstanceOf(DefaultGuardrailService.class);
        var guardrailService = (DefaultGuardrailService) gs;

        assertThat(guardrailService.getInputGuardrailMethodCount()).isEqualTo(2);
        assertThat(guardrailService.getOutputGuardrailMethodCount()).isEqualTo(2);
        assertThat(guardrailService.getInputConfig("chat")).isNotEmpty();

        assertThat(guardrailService.getInputGuardrails("chat"))
                .hasSize(2)
                .satisfiesExactly(
                        guardrail -> assertThat(guardrail).isInstanceOf(IG1.class),
                        guardrail -> assertThat(guardrail).isInstanceOf(IG2.class));

        assertThat(guardrailService.getOutputConfig("chat"))
                .get()
                .extracting(dev.langchain4j.guardrail.config.OutputGuardrailsConfig::maxRetries)
                .isEqualTo(10);

        assertThat(guardrailService.getOutputGuardrails("chat"))
                .hasSize(2)
                .satisfiesExactly(
                        guardrail -> assertThat(guardrail).isInstanceOf(OG1.class),
                        guardrail -> assertThat(guardrail).isInstanceOf(OG2.class));

        assertThat(guardrailService.getInputConfig("chat2")).isNotEmpty();

        assertThat(guardrailService.getInputGuardrails("chat2"))
                .hasSize(2)
                .satisfiesExactly(
                        guardrail -> assertThat(guardrail).isInstanceOf(IG1.class),
                        guardrail -> assertThat(guardrail).isInstanceOf(IG2.class));

        assertThat(guardrailService.getOutputConfig("chat2"))
                .get()
                .extracting(dev.langchain4j.guardrail.config.OutputGuardrailsConfig::maxRetries)
                .isEqualTo(10);

        assertThat(guardrailService.getOutputGuardrails("chat2"))
                .hasSize(2)
                .satisfiesExactly(
                        guardrail -> assertThat(guardrail).isInstanceOf(OG1.class),
                        guardrail -> assertThat(guardrail).isInstanceOf(OG2.class));
    }

    @ParameterizedTest
    @MethodSource("nullOrEmptyBuilderGuardrails")
    void nullOrEmptyBuilderGuardrailsDoNotDisableAnnotationGuardrails(String testDescription, GuardrailService gs) {
        var guardrailService = (DefaultGuardrailService) gs;

        assertThat(guardrailService.getInputGuardrails("chat"))
                .map(Object::getClass)
                .containsExactly(IG1.class, IG2.class);
        assertThat(guardrailService.getInputGuardrails("chat2"))
                .map(Object::getClass)
                .containsExactly(IG1.class);
        assertThat(guardrailService.getOutputGuardrails("chat"))
                .map(Object::getClass)
                .containsExactly(OG1.class, OG2.class);
        assertThat(guardrailService.getOutputGuardrails("chat2"))
                .map(Object::getClass)
                .containsExactly(OG1.class);
        assertThat(guardrailService.getOutputConfig("chat"))
                .get()
                .extracting(OutputGuardrailsConfig::maxRetries)
                .isEqualTo(10);
    }

    @Test
    void methodAnnotationReplacesClassAnnotationAsAWhole() {
        var guardrailService = (DefaultGuardrailService)
                GuardrailService.builder(EmptyMethodAnnotationAssistant.class).build();

        // an empty annotation on the method still replaces the one on the class
        assertThat(guardrailService.getInputGuardrails("chat")).isEmpty();
        assertThat(guardrailService.getInputGuardrails("chat2"))
                .map(Object::getClass)
                .containsExactly(IG1.class);

        // a method annotation without maxRetries uses the default, not the maxRetries of the class annotation
        assertThat(guardrailService.getOutputGuardrails("chat"))
                .map(Object::getClass)
                .containsExactly(OG2.class);
        assertThat(guardrailService.getOutputConfig("chat"))
                .get()
                .extracting(OutputGuardrailsConfig::maxRetries)
                .isEqualTo(OutputGuardrailsConfig.MAX_RETRIES_DEFAULT);
        assertThat(guardrailService.getOutputConfig("chat2"))
                .get()
                .extracting(OutputGuardrailsConfig::maxRetries)
                .isEqualTo(7);
    }

    static Stream<Arguments> nullOrEmptyBuilderGuardrails() {
        return Stream.of(
                Arguments.of(
                        "empty lists",
                        GuardrailService.builder(ClassAndMethodLevelAssistant.class)
                                .inputGuardrailClasses(List.of())
                                .inputGuardrails(List.of())
                                .outputGuardrailClasses(List.of())
                                .outputGuardrails(List.of())
                                .build()),
                Arguments.of(
                        "null lists",
                        GuardrailService.builder(ClassAndMethodLevelAssistant.class)
                                .inputGuardrailClasses((List<Class<? extends InputGuardrail>>) null)
                                .inputGuardrails((List<InputGuardrail>) null)
                                .outputGuardrailClasses((List<Class<? extends OutputGuardrail>>) null)
                                .outputGuardrails((List<OutputGuardrail>) null)
                                .build()),
                Arguments.of(
                        "null arrays",
                        GuardrailService.builder(ClassAndMethodLevelAssistant.class)
                                .inputGuardrails((InputGuardrail[]) null)
                                .outputGuardrails((OutputGuardrail[]) null)
                                .build()),
                Arguments.of(
                        "no guardrails",
                        GuardrailService.builder(ClassAndMethodLevelAssistant.class)
                                .inputGuardrails()
                                .outputGuardrailClasses()
                                .build()));
    }

    static Stream<Arguments> classLevelGuardrailBuilders() {
        return Stream.of(
                Arguments.of(
                        "assistant with annotations",
                        GuardrailService.builder(ClassLevelAssistant.class).build()),
                Arguments.of(
                        "assistant with guardrail classes defined",
                        GuardrailService.builder(NoGuardrailAssistant.class)
                                .inputGuardrailClasses(IG1.class)
                                .outputGuardrailClasses(OG1.class)
                                .build()),
                Arguments.of(
                        "assistant with guardrail instances defined",
                        GuardrailService.builder(NoGuardrailAssistant.class)
                                .inputGuardrails(new IG1())
                                .outputGuardrails(new OG1())
                                .build()));
    }

    static Stream<Arguments> classAndMethodLevelGuardrailBuilders() {
        return Stream.of(
                Arguments.of(
                        "assistant with annotations",
                        GuardrailService.builder(MethodLevelAssistant1.class)
                                .inputGuardrailClasses(IG1.class, IG2.class)
                                .outputGuardrailClasses(OG1.class, OG2.class)
                                .outputGuardrailsConfig(
                                        dev.langchain4j.guardrail.config.OutputGuardrailsConfig.builder()
                                                .maxRetries(10)
                                                .build())
                                .build()),
                Arguments.of(
                        "assistant with guardrail classes defined",
                        GuardrailService.builder(NoGuardrailAssistant.class)
                                .inputGuardrailClasses(IG1.class, IG2.class)
                                .outputGuardrailClasses(OG1.class, OG2.class)
                                .outputGuardrailsConfig(
                                        dev.langchain4j.guardrail.config.OutputGuardrailsConfig.builder()
                                                .maxRetries(10)
                                                .build())
                                .build()),
                Arguments.of(
                        "assistant with guardrail instances defined",
                        GuardrailService.builder(NoGuardrailAssistant.class)
                                .inputGuardrails(new IG1(), new IG2())
                                .outputGuardrails(new OG1(), new OG2())
                                .outputGuardrailsConfig(
                                        dev.langchain4j.guardrail.config.OutputGuardrailsConfig.builder()
                                                .maxRetries(10)
                                                .build())
                                .build()));
    }

    interface NoGuardrailAssistant {
        String chat(String message);

        String chat2(String message);

        static void doSomething() {}
    }

    @InputGuardrails(IG1.class)
    @OutputGuardrails(OG1.class)
    interface ClassLevelAssistant {
        String chat(String message);

        String chat2(String message);

        static void doSomething() {}
    }

    interface MethodLevelAssistant {
        @InputGuardrails(IG1.class)
        @OutputGuardrails(OG1.class)
        String chat(String message);

        @InputGuardrails(IG2.class)
        @OutputGuardrails(OG2.class)
        String chat2(String message);

        static void doSomething() {}
    }

    interface MethodLevelAssistant1 {
        @InputGuardrails({IG1.class, IG2.class})
        @OutputGuardrails(
                value = {OG1.class, OG2.class},
                maxRetries = 10)
        String chat(String message);

        String chat2(String message);

        static void doSomething() {}
    }

    @InputGuardrails(IG1.class)
    @OutputGuardrails(OG1.class)
    interface ClassAndMethodLevelAssistant {
        @InputGuardrails({IG1.class, IG2.class})
        @OutputGuardrails(
                value = {OG1.class, OG2.class},
                maxRetries = 10)
        String chat(String message);

        String chat2(String message);

        static void doSomething() {}
    }

    @InputGuardrails(IG1.class)
    @OutputGuardrails(value = OG1.class, maxRetries = 7)
    interface EmptyMethodAnnotationAssistant {
        @InputGuardrails({})
        @OutputGuardrails(OG2.class)
        String chat(String message);

        String chat2(String message);
    }

    public static class IG1 implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }

    public static class IG2 implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }

    public static class OG1 implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return success();
        }
    }

    public static class OG2 implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return success();
        }
    }
}
