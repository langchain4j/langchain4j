package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
import dev.langchain4j.guardrail.config.ToolOutputGuardrailsConfig;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.guardrail.TestToolGuardrailServiceBuilderFactory.RecordingBuilder;
import dev.langchain4j.service.guardrail.TestToolGuardrailServiceBuilderFactory.RecordingService;
import dev.langchain4j.test.mock.MockChatModel;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

// the tests share static state (the recording builders, the guardrails that record what they saw)
@Execution(ExecutionMode.SAME_THREAD)
class ToolGuardrailServiceBuilderFactoryTest {

    interface SpiAssistant {
        String chat(String message);
    }

    interface OtherAssistant {
        String chat(String message);
    }

    static class WeatherTools {
        @Tool
        String weather() {
            return "sunny";
        }
    }

    static class Accepting implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            return success();
        }
    }

    @Test
    void ai_services_configure_and_use_the_tool_guardrail_service_of_the_spi_factory() {
        var guardrail = new Accepting();
        var config = ToolOutputGuardrailsConfig.builder().build();

        AiServices.builder(SpiAssistant.class)
                .chatModel(MockChatModel.thatResponds("hi"))
                .tools(new WeatherTools())
                .toolOutputGuardrails(guardrail)
                .toolOutputGuardrailsConfig(config)
                .build();

        var builder = TestToolGuardrailServiceBuilderFactory.recordingBuilderFor(SpiAssistant.class)
                .orElseThrow();
        assertThat(builder.outputGuardrails()).containsExactly(guardrail);
        assertThat(builder.outputConfig()).containsSame(config);
        assertThat(builder.builtServices())
                .singleElement()
                .extracting(RecordingService::boundToolNames, InstanceOfAssertFactories.list(String.class))
                .containsExactly("weather");
    }

    @Test
    void the_tool_guardrail_service_of_the_spi_factory_gets_the_input_config_that_disables_the_note() {
        ToolInputGuardrail cappingTheDays = request -> ToolInputGuardrailResult.successWithArguments(Map.of("days", 1));
        var model = MockChatModel.thatResponds(
                AiMessage.from(MockChatModel.toolCall("weather", Map.of("days", 7))), AiMessage.from("done"));

        AiServices.builder(SpiAssistant.class)
                .chatModel(model)
                .tools(new WeatherTools())
                .toolInputGuardrails(cappingTheDays)
                .toolInputGuardrailsConfig(ToolInputGuardrailsConfig.builder()
                        .argumentsRewriteNoteEnabled(false)
                        .build())
                .build()
                .chat("weather?");

        assertThat(model.requests().get(1).messages())
                .filteredOn(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .singleElement()
                .returns("sunny", ToolExecutionResultMessage::text);
    }

    @Test
    void the_factory_receives_the_ai_service_class() {
        assertThat(ToolGuardrailService.builder(SpiAssistant.class)).isInstanceOf(RecordingBuilder.class);
        assertThat(ToolGuardrailService.builder(OtherAssistant.class).build().aiServiceClass())
                .isEqualTo(OtherAssistant.class);
    }
}
