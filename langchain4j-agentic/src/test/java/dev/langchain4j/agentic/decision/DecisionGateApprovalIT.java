package dev.langchain4j.agentic.decision;

import static dev.langchain4j.agentic.decision.DecisionModels.typeSafe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.JsonInMemoryAgenticScopeStore;
import dev.langchain4j.agentic.decision.DecisionAgents.TransactionGate;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.scope.AgenticScopePersister;
import dev.langchain4j.agentic.scope.AgenticSystemSuspendedException;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Scenario 3: a yes/no decision gates a side effect; suspicious transfers wait for a human approval, routine
 * transfers are executed directly.
 */
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
public class DecisionGateApprovalIT {

    private static final String ROUTINE_TRANSFER =
            "12 USD to the bakery around the corner, the usual monthly bread subscription";
    private static final String SUSPICIOUS_TRANSFER =
            "48000 USD to a beneficiary added one hour ago, with an account in an offshore bank, marked urgent, no invoice";

    public interface Transfers {

        @Agent
        String transfer(@MemoryId String transferId, @V("transfer") String transfer);
    }

    private final AtomicInteger moved = new AtomicInteger();

    @BeforeEach
    void setUp() {
        AgenticScopePersister.setStore(new JsonInMemoryAgenticScopeStore());
    }

    @AfterEach
    void tearDown() {
        AgenticScopePersister.setStore(null);
    }

    private Transfers transfers() {
        TransactionGate gate = AgenticServices.decisionAgentBuilder(TransactionGate.class)
                .decisionModel(typeSafe())
                .threshold(0.5)
                .build();
        HumanInTheLoop approval = AgenticServices.humanInTheLoopBuilder()
                .description("Compliance approval of a suspicious transfer")
                .outputKey("approval")
                .responseProvider(scope -> new SuspendedResponse<>("compliance-approval"))
                .activationCondition("the transfer looks suspicious", scope -> scope.readState("suspicious", false))
                .build();
        Object mover = AgenticServices.nonAiAgentBuilder(scope -> {
                    moved.incrementAndGet();
                    String approvalNote = scope.hasState("approval") ? " (" + scope.readState("approval") + ")" : "";
                    return "moved: " + scope.readState("transfer", "") + approvalNote;
                })
                .name("mover")
                .description("Executes the transfer")
                .inputKey(String.class, "transfer")
                .outputKey("receipt")
                .build();
        return AgenticServices.sequenceBuilder(Transfers.class)
                .subAgents(gate, approval, mover)
                .outputKey("receipt")
                .build();
    }

    @Test
    void executes_a_routine_transfer_directly() {
        String receipt = transfers().transfer("t1", ROUTINE_TRANSFER);

        assertThat(receipt).isEqualTo("moved: " + ROUTINE_TRANSFER);
        assertThat(moved).hasValue(1);
    }

    @Test
    void suspends_a_suspicious_transfer_until_a_human_approves_it() {
        Transfers transfers = transfers();

        AgenticSystemSuspendedException suspended = assertThrows(
                AgenticSystemSuspendedException.class, () -> transfers.transfer("t2", SUSPICIOUS_TRANSFER));

        assertThat(suspended.scope().readState("suspicious")).isEqualTo(true);
        assertThat(suspended.scope().pendingResponseIds()).containsExactly("compliance-approval");
        assertThat(moved).hasValue(0);

        suspended.scope().completePendingResponse("approved by compliance");
        String receipt = transfers.transfer("t2", SUSPICIOUS_TRANSFER);

        assertThat(receipt).isEqualTo("moved: " + SUSPICIOUS_TRANSFER + " (approved by compliance)");
        assertThat(moved).hasValue(1);
    }
}
