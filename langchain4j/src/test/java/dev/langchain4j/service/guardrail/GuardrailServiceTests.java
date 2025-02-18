package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import org.junit.jupiter.api.Test;

class GuardrailServiceTests {
    @Test
    void noGuardrails() {
        var guardrailService = new GuardrailService(NoGuardrailAssistant.class);

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
    void classLevelGuardrails() {
        var guardrailService = new GuardrailService(ClassLevelAssistant.class);

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
        var guardrailService = new GuardrailService(MethodLevelAssistant.class);

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
    void classAndMethodLevelGuardrails() {
        var guardrailService = new GuardrailService(ClassAndMethodLevelAssistant.class);

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

    interface NoGuardrailAssistant {
        String chat(String message);

        String chat2(String message);
    }

    @InputGuardrails(IG1.class)
    @OutputGuardrails(OG1.class)
    interface ClassLevelAssistant {
        String chat(String message);

        String chat2(String message);
    }

    interface MethodLevelAssistant {
        @InputGuardrails(IG1.class)
        @OutputGuardrails(OG1.class)
        String chat(String message);

        @InputGuardrails(IG2.class)
        @OutputGuardrails(OG2.class)
        String chat2(String message);
    }

    @InputGuardrails(IG1.class)
    @OutputGuardrails(OG1.class)
    interface ClassAndMethodLevelAssistant {
        @InputGuardrails({IG1.class, IG2.class})
        @OutputGuardrails(
                value = {OG1.class, OG2.class},
                config = @OutputGuardrailsConfig(maxRetries = 10))
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
