package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;

class PlannerSupplierMissingTest {

    public interface MissingSupplierPlannerAgent {

        @PlannerAgent(
                outputKey = "result",
                subAgents = {})
        String run(@V("input") String input);

        // Intentionally no method annotated with @PlannerSupplier.
    }

    @Test
    void missing_planner_supplier_throws_clear_exception() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(MissingSupplierPlannerAgent.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@PlannerSupplier");
    }
}
