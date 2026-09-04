package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.internal.Json;
import dev.langchain4j.model.input.PromptTemplate;
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

    /**
     * The agent renders the argument into its prompt with {{invoice}}, and a template variable
     * becomes text through toString(). A fixture without one asks the model to register
     * "Invoice@4f2a1b3c", which it cannot do, so the supervisor retries until it hits its cap and
     * the failure looks like a wrong invocation count rather than a useless prompt.
     */
    @Test
    void the_prompt_the_agent_sends_describes_the_invoice() {
        Map<String, Object> fromPlanner = Map.of("author", "Mario", "amountInUSD", 100.0);
        SupervisorAgentIT.Invoice invoice =
                Json.fromJson(Json.toJson(fromPlanner), SupervisorAgentIT.Invoice.class);

        String prompt = PromptTemplate.from("Register the invoice described as '{{invoice}}'.")
                .apply(Map.of("invoice", invoice))
                .text();

        assertThat(prompt).contains("Mario").contains("100").doesNotContain("@");
    }
}
