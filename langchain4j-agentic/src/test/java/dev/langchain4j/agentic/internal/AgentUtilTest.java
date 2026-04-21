package dev.langchain4j.agentic.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentUtilTest {

    record Address(String street, String city) {}

    record Person(String name, int age, Address address) {}

    @Test
    void should_pass_string_argument_as_is() throws Exception {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        scope.writeState("name", "Alice");

        AgentInvocationArguments args =
                AgentUtil.agentInvocationArguments(scope, List.of(new AgentArgument(String.class, "name")));

        assertThat(args.positionalArgs()[0]).isEqualTo("Alice");
    }

    @Test
    void should_deserialize_json_string_to_custom_object() throws Exception {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        scope.writeState(
                "person", "{\"name\":\"Alice\",\"age\":30,\"address\":{\"street\":\"Main St\",\"city\":\"NY\"}}");

        AgentInvocationArguments args =
                AgentUtil.agentInvocationArguments(scope, List.of(new AgentArgument(Person.class, "person")));

        Person person = (Person) args.positionalArgs()[0];
        assertThat(person.name()).isEqualTo("Alice");
        assertThat(person.age()).isEqualTo(30);
        assertThat(person.address().city()).isEqualTo("NY");
    }

    @Test
    void should_pass_custom_object_directly_when_already_correct_type() throws Exception {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        Person alice = new Person("Alice", 30, new Address("Main St", "NY"));
        scope.writeState("person", alice);

        AgentInvocationArguments args =
                AgentUtil.agentInvocationArguments(scope, List.of(new AgentArgument(Person.class, "person")));

        assertThat(args.positionalArgs()[0]).isSameAs(alice);
    }

    @Test
    void should_coerce_string_to_integer() throws Exception {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        scope.writeState("count", "42");

        AgentInvocationArguments args =
                AgentUtil.agentInvocationArguments(scope, List.of(new AgentArgument(Integer.class, "count")));

        assertThat(args.positionalArgs()[0]).isEqualTo(42);
    }

    @Test
    void should_coerce_string_to_boolean() throws Exception {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        scope.writeState("flag", "true");

        AgentInvocationArguments args =
                AgentUtil.agentInvocationArguments(scope, List.of(new AgentArgument(Boolean.class, "flag")));

        assertThat(args.positionalArgs()[0]).isEqualTo(true);
    }

    @Test
    void should_throw_when_json_string_is_invalid_for_target_type() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        scope.writeState("person", "not-valid-json");

        assertThatThrownBy(() ->
                        AgentUtil.agentInvocationArguments(scope, List.of(new AgentArgument(Person.class, "person"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
