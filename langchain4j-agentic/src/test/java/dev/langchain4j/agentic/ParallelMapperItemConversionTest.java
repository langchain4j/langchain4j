package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * A parallel mapper passes each item to its subagent converted to the type of the subagent's argument, as a value read
 * from the agentic scope is, and reports the item as the input of the subagent's invocation.
 */
class ParallelMapperItemConversionTest {

    public record Person(String name, String sign) {}

    public static class HoroscopeAgent {

        @Agent(outputKey = "horoscope")
        public String horoscope(@V("person") Person person) {
            return person.name() + " is " + person.sign();
        }
    }

    public static class SquareAgent {

        @Agent(outputKey = "square")
        public int square(@V("number") int number) {
            return number * number;
        }
    }

    static class NotConstructible {
        private final String name;

        NotConstructible(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class NotConstructibleAgent {

        @Agent(outputKey = "name")
        public String name(@V("doc") NotConstructible doc) {
            return doc.getName();
        }
    }

    @Test
    void map_items_are_converted_to_a_record_argument_as_in_a_sequence() {
        Map<String, Object> mario = Map.of("name", "Mario", "sign", "aries");
        Map<String, Object> luigi = Map.of("name", "Luigi", "sign", "leo");

        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(new HoroscopeAgent())
                .outputKey("horoscope")
                .build();
        UntypedAgent mapper = AgenticServices.parallelMapperBuilder()
                .subAgents(new HoroscopeAgent())
                .itemsProvider("persons")
                .outputKey("horoscopes")
                .build();

        assertThat(sequence.invoke(Map.of("person", mario))).isEqualTo("Mario is aries");
        assertThat(mapper.invoke(Map.of("persons", List.of(mario, luigi))))
                .isEqualTo(List.of("Mario is aries", "Luigi is leo"));
    }

    @Test
    void items_of_the_argument_type_are_passed_as_they_are() {
        UntypedAgent mapper = AgenticServices.parallelMapperBuilder()
                .subAgents(new HoroscopeAgent())
                .itemsProvider("persons")
                .outputKey("horoscopes")
                .build();

        assertThat(mapper.invoke(Map.of("persons", List.of(new Person("Mario", "aries")))))
                .isEqualTo(List.of("Mario is aries"));
    }

    @Test
    void string_items_are_converted_to_a_primitive_argument() {
        UntypedAgent mapper = AgenticServices.parallelMapperBuilder()
                .subAgents(new SquareAgent())
                .itemsProvider("numbers")
                .outputKey("squares")
                .build();

        assertThat(mapper.invoke(Map.of("numbers", List.of("2", "3")))).isEqualTo(List.of(4, 9));
    }

    @Test
    void an_item_that_cannot_be_converted_names_the_argument() {
        UntypedAgent mapper = AgenticServices.parallelMapperBuilder()
                .subAgents(new NotConstructibleAgent())
                .itemsProvider("docs")
                .outputKey("names")
                .build();

        assertThatThrownBy(() -> mapper.invoke(Map.of("docs", List.of(Map.of("name", "Mario")))))
                .hasStackTraceContaining("Cannot read agent argument 'doc' as " + NotConstructible.class.getName());
    }

    @Test
    void listeners_see_each_item_as_the_input_of_its_invocation() {
        List<Map<String, Object>> inputs = new CopyOnWriteArrayList<>();
        UntypedAgent mapper = AgenticServices.parallelMapperBuilder()
                .subAgents(new SquareAgent())
                .itemsProvider("numbers")
                .outputKey("squares")
                .listener(new AgentListener() {
                    @Override
                    public void beforeAgentInvocation(AgentRequest agentRequest) {
                        if (agentRequest.agentName().startsWith("square")) {
                            inputs.add(agentRequest.inputs());
                        }
                    }

                    @Override
                    public boolean inheritedBySubagents() {
                        return true;
                    }
                })
                .build();

        mapper.invoke(Map.of("numbers", List.of(2, 3)));

        assertThat(inputs).containsExactlyInAnyOrder(Map.of("number", 2), Map.of("number", 3));
    }
}
