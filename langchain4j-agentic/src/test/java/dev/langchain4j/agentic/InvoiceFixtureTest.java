package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.internal.Json;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The planner hands an agent argument over as a Map, which AgentUtil.adaptValueToType reads into the
 * declared parameter type. Covers that SupervisorAgentIT's Invoice can survive that, without needing
 * a model.
 */
class InvoiceFixtureTest {

    @Test
    void invoice_is_readable_from_the_map_a_planner_produces() {
        Map<String, Object> fromPlanner = Map.of("author", "Mario", "amountInUSD", 100.0);

        SupervisorAgentIT.Invoice invoice =
                Json.fromJson(Json.toJson(fromPlanner), SupervisorAgentIT.Invoice.class);

        assertThat(invoice.getAuthor()).isEqualTo("Mario");
        assertThat(invoice.getAmountInUSD()).isEqualTo(100.0);
    }
}
