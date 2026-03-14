package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.internal.DelayedResponse;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorPlanner;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the type guard logic in {@code SupervisorPlanner.writeArgumentToScope()}.
 *
 * <p>Regression tests for <a href="https://github.com/langchain4j/langchain4j/issues/4686">#4686</a>:
 * the guard blocked repeated invocations of the same agent because
 * {@code isAssignableFrom} was called in the wrong direction.
 *
 * @see <a href="https://github.com/langchain4j/langchain4j/pull/4381">#4381</a> (introduced the guard)
 */
class WriteArgumentToScopeTest {

    private SupervisorPlanner planner;
    private Method writeArgumentToScope;
    private AgenticScope scope;

    @BeforeEach
    void setUp() throws Exception {
        planner = new SupervisorPlanner(
                null,
                null,
                10,
                SupervisorContextStrategy.CHAT_MEMORY,
                SupervisorResponseStrategy.LAST,
                null,
                null,
                null);

        writeArgumentToScope = SupervisorPlanner.class.getDeclaredMethod(
                "writeArgumentToScope", AgenticScope.class, AgentInstance.class, String.class, Object.class);
        writeArgumentToScope.setAccessible(true);

        Constructor<DefaultAgenticScope> ctor =
                DefaultAgenticScope.class.getDeclaredConstructor(DefaultAgenticScope.Kind.class);
        ctor.setAccessible(true);
        scope = ctor.newInstance(DefaultAgenticScope.Kind.EPHEMERAL);
    }

    private boolean shouldWrite(AgentInstance agent, String key, Object value) throws Exception {
        return (boolean) writeArgumentToScope.invoke(planner, scope, agent, key, value);
    }

    private static AgentInstance stubAgent(String name, List<AgentArgument> arguments) {
        return new StubAgentInstance(name, arguments);
    }

    // -- repeated invocation: compatible values should overwrite --

    @Test
    void should_allow_overwriting_map_with_compatible_map() throws Exception {
        AgentInstance agent = stubAgent("agent", List.of(new AgentArgument(Map.class, "data")));

        // First call writes to scope
        scope.writeState("data", new LinkedHashMap<>(Map.of("id", 1)));

        // Second call should be allowed
        assertThat(shouldWrite(agent, "data", new LinkedHashMap<>(Map.of("id", 2))))
                .isTrue();
    }

    @Test
    void should_allow_overwriting_list_with_compatible_list() throws Exception {
        AgentInstance agent = stubAgent("agent", List.of(new AgentArgument(List.class, "items")));

        scope.writeState("items", new ArrayList<>(List.of("a")));

        assertThat(shouldWrite(agent, "items", new ArrayList<>(List.of("b")))).isTrue();
    }

    @Test
    void should_allow_overwriting_object_with_any_value() throws Exception {
        AgentInstance agent = stubAgent("agent", List.of(new AgentArgument(Object.class, "payload")));

        scope.writeState("payload", new LinkedHashMap<>(Map.of("old", 1)));

        assertThat(shouldWrite(agent, "payload", new LinkedHashMap<>(Map.of("new", 2))))
                .isTrue();
    }

    @Test
    void should_allow_overwriting_string_with_string() throws Exception {
        AgentInstance agent = stubAgent("agent", List.of(new AgentArgument(String.class, "name")));

        scope.writeState("name", "Alice");

        assertThat(shouldWrite(agent, "name", "Bob")).isTrue();
    }

    // -- protection: unstructured should not overwrite structured (#4381) --

    @Test
    void should_block_map_overwriting_pojo() throws Exception {
        AgentInstance agent = stubAgent("agent", List.of(new AgentArgument(LoanApplication.class, "loan")));

        scope.writeState("loan", new LoanApplication("John", 30, 80000));

        assertThat(shouldWrite(agent, "loan", new LinkedHashMap<>(Map.of("applicantName", "John"))))
                .isFalse();
    }

    @Test
    void should_block_string_overwriting_pojo() throws Exception {
        AgentInstance agent = stubAgent("agent", List.of(new AgentArgument(LoanApplication.class, "loan")));

        scope.writeState("loan", new LoanApplication("John", 30, 80000));

        assertThat(shouldWrite(agent, "loan", "raw string from LLM")).isFalse();
    }

    // -- edge: existing value is incompatible with argType → allow overwrite --

    @Test
    void should_allow_overwrite_when_existing_value_is_incompatible() throws Exception {
        AgentInstance agent = stubAgent("agent", List.of(new AgentArgument(LoanApplication.class, "loan")));

        // Scope has a LinkedHashMap but argType expects LoanApplication — incompatible existing value
        scope.writeState("loan", new LinkedHashMap<>(Map.of("stale", true)));

        assertThat(shouldWrite(agent, "loan", new LinkedHashMap<>(Map.of("also_stale", true))))
                .isTrue();
    }

    // -- edge: null value should not overwrite structured state --

    @Test
    void should_block_null_overwriting_typed_state() throws Exception {
        AgentInstance agent = stubAgent("agent", List.of(new AgentArgument(LoanApplication.class, "loan")));

        scope.writeState("loan", new LoanApplication("John", 30, 80000));

        assertThat(shouldWrite(agent, "loan", null)).isFalse();
    }

    // -- edge: existing value resolved to null (async agent) → allow overwrite --

    @Test
    void should_allow_overwrite_when_existing_value_resolves_to_null() throws Exception {
        AgentInstance agent = stubAgent("agent", List.of(new AgentArgument(Map.class, "data")));

        // Write a DelayedResponse that resolves to null — simulates an async agent whose output is null.
        // hasState() sees a non-null object → true, but readState() resolves it to null.
        scope.writeState("data", new DelayedResponse<Object>() {
            @Override
            public boolean isDone() {
                return true;
            }

            @Override
            public Object blockingGet() {
                return null;
            }
        });

        assertThat(shouldWrite(agent, "data", new LinkedHashMap<>(Map.of("id", 1))))
                .isTrue();
    }

    // -- edge: no existing state → always allow first write --

    @Test
    void should_allow_first_write_when_scope_has_no_existing_state() throws Exception {
        AgentInstance agent = stubAgent("agent", List.of(new AgentArgument(Map.class, "data")));

        assertThat(shouldWrite(agent, "data", new LinkedHashMap<>(Map.of("id", 1))))
                .isTrue();
    }

    // -- edge: key not declared on agent → allow write --

    @Test
    void should_allow_write_when_key_is_not_declared_on_agent() throws Exception {
        AgentInstance agent = stubAgent("agent", List.of(new AgentArgument(String.class, "name")));

        scope.writeState("extra", "old");

        assertThat(shouldWrite(agent, "extra", "new")).isTrue();
    }

    // -- edge: Number subtype compatibility --

    @Test
    void should_allow_overwriting_number_with_other_number_subtype() throws Exception {
        AgentInstance agent = stubAgent("agent", List.of(new AgentArgument(Number.class, "amount")));

        scope.writeState("amount", 1);

        assertThat(shouldWrite(agent, "amount", 2.5)).isTrue();
    }

    record LoanApplication(String applicantName, int applicantAge, int amount) {}
}
