package dev.langchain4j.agentic.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests the generation of the argument descriptions the supervisor includes in
 * its agent cards ({@link SupervisorPlanner#argumentDescription(Class, String)}),
 * in particular for argument types extending a base class.
 */
class SupervisorArgumentDescriptionTest {

    static class BaseTask {

        private final String title;

        BaseTask(String title) {
            this.title = title;
        }
    }

    static class ExtendingTask extends BaseTask {

        private final int priority;

        ExtendingTask(String title, int priority) {
            super(title);
            this.priority = priority;
        }
    }

    static class ShadowingTask extends BaseTask {

        private final int title;

        ShadowingTask(int title) {
            super(null);
            this.title = title;
        }
    }

    record Point(int x, int y) {}

    static class NestedHolder {

        private final ExtendingTask task;

        NestedHolder(ExtendingTask task) {
            this.task = task;
        }
    }

    @Test
    void should_describe_inherited_fields_of_extending_class() {
        String description = SupervisorPlanner.argumentDescription(ExtendingTask.class, "task");
        // Superclass fields first, then the subclass's own fields.
        assertThat(description).isEqualTo("task: {title: String, priority: int}");
    }

    @Test
    void should_not_duplicate_field_shadowed_by_subclass() {
        String description = SupervisorPlanner.argumentDescription(ShadowingTask.class, "task");
        // The subclass redeclaration shadows the inherited String field.
        assertThat(description).isEqualTo("task: {title: int}");
    }

    @Test
    void should_keep_record_descriptions_unchanged() {
        String description = SupervisorPlanner.argumentDescription(Point.class, "point");
        assertThat(description).isEqualTo("point: {x: int, y: int}");
    }

    @Test
    void should_describe_simple_types_as_before() {
        assertThat(SupervisorPlanner.argumentDescription(String.class, "query")).isEqualTo("query: String");
        assertThat(SupervisorPlanner.argumentDescription(int.class, "count")).isEqualTo("count: int");
        assertThat(SupervisorPlanner.argumentDescription(Double.class, "score")).isEqualTo("score: Double");
    }

    @Test
    void should_recurse_into_nested_types_honoring_their_superclasses() {
        String description = SupervisorPlanner.argumentDescription(NestedHolder.class, "holder");
        assertThat(description).isEqualTo("holder: {task: {title: String, priority: int}}");
    }

    @Test
    void should_return_empty_string_for_null_name() {
        assertThat(SupervisorPlanner.argumentDescription(ExtendingTask.class, null))
                .isEmpty();
    }
}
