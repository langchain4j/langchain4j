package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.V;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A parallel mapper passes each item to the first argument of its subagent that is not provided by the framework, so
 * the subagent can declare an argument like its {@link AgenticScope} before the one receiving the item.
 */
class ParallelMapperFrameworkArgumentTest {

    public static class ScopeFirstDoubler {

        @Agent(description = "Doubles a number", outputKey = "doubled")
        public int doubleIt(AgenticScope agenticScope, @V("number") int number) {
            return number * 2;
        }
    }

    public static class ScopeLastDoubler {

        @Agent(description = "Doubles a number", outputKey = "doubled")
        public int doubleIt(@V("number") int number, AgenticScope agenticScope) {
            return number * 2;
        }
    }

    public static class ParametersAndScopeFirstDoubler {

        @Agent(description = "Doubles a number", outputKey = "doubled")
        public int doubleIt(
                InvocationParameters invocationParameters, AgenticScope agenticScope, @V("number") int number) {
            return number * 2;
        }
    }

    public static class ScopeOnlyAgent {

        @Agent(description = "Reads the number of the scope", outputKey = "read")
        public int read(AgenticScope agenticScope) {
            return (int) agenticScope.readState("number", 0);
        }
    }

    public interface Mapper {

        @Agent
        List<Integer> mapAll(@V("numbers") List<Integer> numbers);
    }

    @Test
    void item_is_passed_to_the_argument_after_the_agentic_scope() {
        assertThat(mapper(new ScopeFirstDoubler()).mapAll(List.of(1, 2, 3))).containsExactly(2, 4, 6);
    }

    @Test
    void item_is_passed_to_the_argument_before_the_agentic_scope() {
        assertThat(mapper(new ScopeLastDoubler()).mapAll(List.of(1, 2, 3))).containsExactly(2, 4, 6);
    }

    @Test
    void item_is_passed_to_the_argument_after_several_framework_arguments() {
        assertThat(mapper(new ParametersAndScopeFirstDoubler()).mapAll(List.of(1, 2)))
                .containsExactly(2, 4);
    }

    @Test
    void subagent_with_only_framework_arguments_is_invoked_once_per_item() {
        assertThat(mapper(new ScopeOnlyAgent()).mapAll(List.of(1, 2))).containsExactly(0, 0);
    }

    private static Mapper mapper(Object subagent) {
        return AgenticServices.parallelMapperBuilder(Mapper.class)
                .subAgents(subagent)
                .build();
    }
}
