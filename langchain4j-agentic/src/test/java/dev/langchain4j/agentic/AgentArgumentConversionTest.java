package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.internal.AgentUtil;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.service.V;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A planner describes an agent argument as JSON, so the declared parameter type has to be one a JSON
 * library can build. When it is not, the failure should name the argument and say what to do, rather
 * than pass on the JSON library's own wording.
 */
class AgentArgumentConversionTest {

    static class NotConstructible {
        private final String author;

        NotConstructible(String author) {
            this.author = author;
        }

        public String getAuthor() {
            return author;
        }
    }

    record Constructible(String author, double amountInUSD) {}

    interface RegistrationAgent {
        @Agent("registers something")
        String register(@V("doc") NotConstructible doc);
    }

    interface RecordAgent {
        @Agent("registers something")
        String register(@V("doc") Constructible doc);
    }

    @Test
    void a_type_the_planner_cannot_build_names_the_argument_and_the_remedy() {
        assertThatThrownBy(() -> arguments(RegistrationAgent.class, Map.of("author", "Mario")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("doc")
                .hasMessageContaining(NotConstructible.class.getName())
                .hasMessageContaining("@JsonCreator")
                .hasMessageContaining("record");
    }

    @Test
    void the_message_does_not_repeat_what_the_model_sent() {
        assertThatThrownBy(() -> arguments(RegistrationAgent.class, Map.of("author", "some-sensitive-value")))
                .hasMessageNotContaining("some-sensitive-value");
    }

    @Test
    void a_record_argument_is_built_from_the_planners_map() {
        Object argument = arguments(RecordAgent.class, Map.of("author", "Mario", "amountInUSD", 100.0));

        assertThat(argument).isInstanceOf(Constructible.class);
        assertThat(((Constructible) argument).author()).isEqualTo("Mario");
    }

    private static Object arguments(Class<?> agentClass, Map<String, Object> plannerValue) {
        DefaultAgenticScope scope = new AgenticScopeRegistry("test-agent").create("test-memory");
        scope.writeState("doc", plannerValue);
        Method method = agentClass.getDeclaredMethods()[0];
        return AgentUtil.agentInvocationArguments(scope, method).positionalArgs()[0];
    }
}
