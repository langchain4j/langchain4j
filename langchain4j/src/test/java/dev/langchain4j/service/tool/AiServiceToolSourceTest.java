package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSource;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.test.mock.MockChatModel;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * Without an explicit source, a tool backed by a {@link DefaultToolExecutor} is local, with the original method of the
 * executor, however it is registered, and any other tool is programmatic.
 */
class AiServiceToolSourceTest {

    interface Assistant {

        String chat(@UserMessage String message);
    }

    public static class Weather {

        @Tool
        public String forecast(String city) {
            return "sunny";
        }
    }

    /** What a framework registers for a proxied bean, such as a Spring CGLIB proxy: a subclass of the bean. */
    public static class WeatherProxy extends Weather {

        @Override
        public String forecast(String city) {
            return super.forecast(city);
        }
    }

    private static Method forecast(Class<?> type) throws NoSuchMethodException {
        return type.getMethod("forecast", String.class);
    }

    @Test
    void a_tool_backed_by_a_default_tool_executor_is_local() throws Exception {
        var method = forecast(Weather.class);

        var tool = AiServiceTool.builder()
                .toolSpecification(ToolSpecifications.toolSpecificationFrom(method))
                .toolExecutor(new DefaultToolExecutor(new Weather(), method))
                .build();

        assertThat(tool.toolSource()).isEqualTo(ToolSource.local(method));
    }

    @Test
    void a_proxied_bean_is_local_with_the_class_of_the_application_not_the_proxy() throws Exception {
        var originalMethod = forecast(Weather.class);
        var proxyMethod = forecast(WeatherProxy.class);

        var tool = AiServiceTool.builder()
                .toolSpecification(ToolSpecifications.toolSpecificationFrom(originalMethod))
                .toolExecutor(new DefaultToolExecutor(new WeatherProxy(), originalMethod, proxyMethod))
                .build();

        assertThat(tool.toolSource())
                .asInstanceOf(type(ToolSource.Local.class))
                .returns(Weather.class.getName(), ToolSource.Local::declaringClassName)
                .returns("forecast", ToolSource.Local::methodName);
    }

    @Test
    void an_explicit_source_wins() throws Exception {
        var method = forecast(Weather.class);

        var tool = AiServiceTool.builder()
                .toolSpecification(ToolSpecifications.toolSpecificationFrom(method))
                .toolExecutor(new DefaultToolExecutor(new Weather(), method))
                .toolSource(ToolSource.programmatic())
                .build();

        assertThat(tool.toolSource()).isEqualTo(ToolSource.programmatic());
    }

    @Test
    void a_tool_with_another_executor_is_programmatic() {
        var tool = AiServiceTool.builder()
                .toolSpecification(ToolSpecification.builder().name("forecast").build())
                .toolExecutor((request, memoryId) -> "sunny")
                .build();

        assertThat(tool.toolSource()).isEqualTo(ToolSource.programmatic());
    }

    @Test
    void a_proxied_bean_registered_through_a_map_reaches_the_guardrails_as_local() throws Exception {
        var originalMethod = forecast(Weather.class);
        var specification = ToolSpecifications.toolSpecificationFrom(originalMethod);
        var sources = new CopyOnWriteArrayList<ToolSource>();
        ToolInputGuardrail recording = request -> {
            sources.add(request.toolSource());
            return ToolInputGuardrailResult.success();
        };
        var model = MockChatModel.thatResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("1")
                        .name(specification.name())
                        .arguments("{\"arg0\": \"Paris\"}")
                        .build()),
                AiMessage.from("done"));

        AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(Map.<ToolSpecification, ToolExecutor>of(
                        specification,
                        new DefaultToolExecutor(new WeatherProxy(), originalMethod, forecast(WeatherProxy.class))))
                .toolInputGuardrails(recording)
                .build()
                .chat("weather?");

        assertThat(sources)
                .singleElement()
                .asInstanceOf(type(ToolSource.Local.class))
                .returns(Weather.class.getName(), ToolSource.Local::declaringClassName);
    }

    @Test
    void a_lambda_tool_registered_through_a_map_reaches_the_guardrails_as_programmatic() {
        var specification = ToolSpecification.builder().name("forecast").build();
        var sources = new CopyOnWriteArrayList<ToolSource>();
        ToolInputGuardrail recording = request -> {
            sources.add(request.toolSource());
            return ToolInputGuardrailResult.success();
        };
        var model = MockChatModel.thatResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("1")
                        .name("forecast")
                        .arguments("{}")
                        .build()),
                AiMessage.from("done"));

        AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(Map.<ToolSpecification, ToolExecutor>of(specification, (request, memoryId) -> "sunny"))
                .toolInputGuardrails(recording)
                .build()
                .chat("weather?");

        assertThat(sources).containsExactly(ToolSource.programmatic());
    }
}
