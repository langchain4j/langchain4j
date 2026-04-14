package dev.langchain4j.agentic.scope;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionContextTest {

    @Test
    void should_store_and_retrieve_execution_context() {
        // given
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        String context = "test-context";

        // when
        scope.setExecutionContext(String.class, context);

        // then
        String retrieved = scope.getExecutionContext(String.class);
        assertThat(retrieved).isEqualTo(context);
    }

    @Test
    void should_handle_multiple_execution_contexts_of_different_types() {
        // given
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        String stringContext = "string-context";
        Integer integerContext = 42;
        TestPlanner plannerContext = new TestPlanner("planner-1");

        // when
        scope.setExecutionContext(String.class, stringContext);
        scope.setExecutionContext(Integer.class, integerContext);
        scope.setExecutionContext(TestPlanner.class, plannerContext);

        // then
        assertThat(scope.getExecutionContext(String.class)).isEqualTo(stringContext);
        assertThat(scope.getExecutionContext(Integer.class)).isEqualTo(integerContext);
        assertThat(scope.getExecutionContext(TestPlanner.class)).isEqualTo(plannerContext);
    }

    @Test
    void should_return_null_for_non_existent_execution_context() {
        // given
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();

        // when
        String retrieved = scope.getExecutionContext(String.class);

        // then
        assertThat(retrieved).isNull();
    }

    @Test
    void should_override_execution_context_when_set_multiple_times() {
        // given
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        String firstContext = "first";
        String secondContext = "second";

        // when
        scope.setExecutionContext(String.class, firstContext);
        scope.setExecutionContext(String.class, secondContext);

        // then
        String retrieved = scope.getExecutionContext(String.class);
        assertThat(retrieved).isEqualTo(secondContext);
    }

    @Test
    void should_throw_exception_when_setting_null_execution_context() {
        // given
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();

        // when/then
        assertThatThrownBy(() -> scope.setExecutionContext(String.class, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("executionContext cannot be null");
    }

    @Test
    void should_not_serialize_execution_context() {
        // given
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);
        TestPlanner planner = new TestPlanner("test-planner");

        scope.setExecutionContext(TestPlanner.class, planner);
        scope.writeState("serializable-key", "serializable-value");

        // when
        String json = AgenticScopeSerializer.toJson(scope);
        DefaultAgenticScope deserialized = AgenticScopeSerializer.fromJson(json);

        // then
        assertThat(deserialized.readState("serializable-key")).isEqualTo("serializable-value");
        assertThat(deserialized.getExecutionContext(TestPlanner.class)).isNull();
    }

    @Test
    void should_maintain_execution_context_across_state_operations() {
        // given
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        TestPlanner planner = new TestPlanner("planner");

        scope.setExecutionContext(TestPlanner.class, planner);

        // when
        scope.writeState("key1", "value1");
        scope.writeState("key2", "value2");
        scope.readState("key1");

        // then
        assertThat(scope.getExecutionContext(TestPlanner.class)).isEqualTo(planner);
    }

    @Test
    void should_handle_execution_context_for_ephemeral_scopes() {
        // given
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        TestPlanner planner = new TestPlanner("ephemeral-planner");

        // when
        scope.setExecutionContext(TestPlanner.class, planner);

        // then
        assertThat(scope.getExecutionContext(TestPlanner.class)).isEqualTo(planner);
    }

    @Test
    void should_handle_execution_context_for_persistent_scopes() {
        // given
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);
        TestPlanner planner = new TestPlanner("persistent-planner");

        // when
        scope.setExecutionContext(TestPlanner.class, planner);

        // then
        assertThat(scope.getExecutionContext(TestPlanner.class)).isEqualTo(planner);
    }

    @Test
    void should_handle_execution_context_for_registered_scopes() {
        // given
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.REGISTERED);
        TestPlanner planner = new TestPlanner("registered-planner");

        // when
        scope.setExecutionContext(TestPlanner.class, planner);

        // then
        assertThat(scope.getExecutionContext(TestPlanner.class)).isEqualTo(planner);
    }

    // Test helper class
    private static class TestPlanner {
        private final String name;

        TestPlanner(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TestPlanner that = (TestPlanner) obj;
            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }
}
