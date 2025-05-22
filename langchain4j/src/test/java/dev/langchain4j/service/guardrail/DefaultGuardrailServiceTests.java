package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
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
