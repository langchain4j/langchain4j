package dev.langchain4j.agentic.scope;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionContextTest {

    @Test
    void should_store_and_retrieve_execution_context_by_string_key() {
        // given
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        String context = "test-context";

        // when
        scope.writeExecutionContext("myKey", context);

        // then
        String retrieved = scope.executionContextAs("myKey", String.class);
        assertThat(retrieved).isEqualTo(context);
    }

    @Test
    void should_store_and_retrieve_execution_context_by_class() {
        // given
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        String context = "test-context";

        // when
        scope.writeExecutionContext(String.class, context);

        // then
        String retrieved = scope.executionContextAs(String.class);
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
        scope.writeExecutionContext(String.class, stringContext);
        scope.writeExecutionContext(Integer.class, integerContext);
        scope.writeExecutionContext(TestPlanner.class, plannerContext);

        // then
        assertThat(scope.executionContextAs(String.class)).isEqualTo(stringContext);
        assertThat(scope.executionContextAs(Integer.class)).isEqualTo(integerContext);
        assertThat(scope.executionContextAs(TestPlanner.class)).isEqualTo(plannerContext);
    }

    @Test
    void should_allow_multiple_contexts_of_same_type_with_different_keys() {
        // given
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        String context1 = "first-string";
        String context2 = "second-string";

        // when
        scope.writeExecutionContext("key1", context1);
        scope.writeExecutionContext("key2", context2);

        // then
        assertThat(scope.executionContextAs("key1", String.class)).isEqualTo(context1);
        assertThat(scope.executionContextAs("key2", String.class)).isEqualTo(context2);
    }

    @Test
    void should_return_null_for_non_existent_execution_context() {
        // given
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();

        // when
        String retrieved = scope.executionContextAs("nonExistentKey", String.class);

        // then
        assertThat(retrieved).isNull();
    }

    @Test
    void should_return_null_for_non_existent_execution_context_by_class() {
        // given
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();

        // when
        String retrieved = scope.executionContextAs(String.class);

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
        scope.writeExecutionContext("key", firstContext);
        scope.writeExecutionContext("key", secondContext);

        // then
        String retrieved = scope.executionContextAs("key", String.class);
        assertThat(retrieved).isEqualTo(secondContext);
    }

    @Test
    void should_throw_exception_when_writing_null_execution_context() {
        // given
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();

        // when/then
        assertThatThrownBy(() -> scope.writeExecutionContext("key", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("context cannot be null");
    }

    @Test
    void should_throw_exception_when_writing_execution_context_with_null_key() {
        // given
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();

        // when/then
        assertThatThrownBy(() -> scope.writeExecutionContext((String) null, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key cannot be null");
    }

    @Test
    void should_not_serialize_execution_context() {
        // given
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);
        TestPlanner planner = new TestPlanner("test-planner");

        scope.writeExecutionContext("planner", planner);
        scope.writeExecutionContext(TestPlanner.class, planner);
        scope.writeState("serializable-key", "serializable-value");

        // when
        String json = AgenticScopeSerializer.toJson(scope);
        DefaultAgenticScope deserialized = AgenticScopeSerializer.fromJson(json);

        // then
        assertThat(deserialized.readState("serializable-key")).isEqualTo("serializable-value");
        assertThat(deserialized.executionContextAs("planner", TestPlanner.class)).isNull();
        assertThat(deserialized.executionContextAs(TestPlanner.class)).isNull();
    }

    @Test
    void should_maintain_execution_context_across_state_operations() {
        // given
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        TestPlanner planner = new TestPlanner("planner");

        scope.writeExecutionContext(TestPlanner.class, planner);

        // when
        scope.writeState("key1", "value1");
        scope.writeState("key2", "value2");
        scope.readState("key1");

        // then
        assertThat(scope.executionContextAs(TestPlanner.class)).isEqualTo(planner);
    }

    @Test
    void should_handle_execution_context_for_ephemeral_scopes() {
        // given
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        TestPlanner planner = new TestPlanner("ephemeral-planner");

        // when
        scope.writeExecutionContext(TestPlanner.class, planner);

        // then
        assertThat(scope.executionContextAs(TestPlanner.class)).isEqualTo(planner);
    }

    @Test
    void should_handle_execution_context_for_persistent_scopes() {
        // given
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);
        TestPlanner planner = new TestPlanner("persistent-planner");

        // when
        scope.writeExecutionContext(TestPlanner.class, planner);

        // then
        assertThat(scope.executionContextAs(TestPlanner.class)).isEqualTo(planner);
    }

    @Test
    void should_handle_execution_context_for_registered_scopes() {
        // given
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.REGISTERED);
        TestPlanner planner = new TestPlanner("registered-planner");

        // when
        scope.writeExecutionContext(TestPlanner.class, planner);

        // then
        assertThat(scope.executionContextAs(TestPlanner.class)).isEqualTo(planner);
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
