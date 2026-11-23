package dev.langchain4j.model.openai.realtime.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RealtimeToolRegistryTest {

    @Test
    void lookupByName_returnsExecutorAndSpecification() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("get_weather")
                .description("weather")
                .build();
        ToolExecutor executor = (req, mem) -> "sunny";

        RealtimeToolRegistry registry = RealtimeToolRegistry.from(Map.of(spec, executor));

        assertThat(registry.findExecutor("get_weather")).isSameAs(executor);
        assertThat(registry.findSpecification("get_weather")).isEqualTo(spec);
        assertThat(registry.allSpecifications()).containsExactly(spec);
        assertThat(registry.names()).containsExactly("get_weather");
    }

    @Test
    void unknownName_returnsNull() {
        RealtimeToolRegistry registry = RealtimeToolRegistry.from(Map.of());

        assertThat(registry.findExecutor("nope")).isNull();
        assertThat(registry.findSpecification("nope")).isNull();
    }

    @Test
    void duplicateName_isRejected() {
        ToolSpecification first = ToolSpecification.builder()
                .name("get_weather")
                .description("first")
                .build();
        ToolSpecification second = ToolSpecification.builder()
                .name("get_weather")
                .description("second")
                .build();
        Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>();
        tools.put(first, (req, mem) -> "a");
        tools.put(second, (req, mem) -> "b");

        assertThatThrownBy(() -> RealtimeToolRegistry.from(tools))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("get_weather");
    }
}
