package dev.langchain4j.agentic.patterns.htn;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.agentic.patterns.htn.DecompositionMethod.decompose;
import static dev.langchain4j.agentic.patterns.htn.TaskNode.compound;
import static dev.langchain4j.agentic.patterns.htn.TaskNode.primitive;
import static dev.langchain4j.agentic.patterns.htn.TaskNode.primitives;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HtnPlannerTest {

    private static final List<String> executionOrder = new ArrayList<>();

    @Test
    void static_tree_traversal() {
        executionOrder.clear();

        TaskNode tree = compound("Produce Annual Report",
                compound("Gather Financial Data",
                        primitive(QueryRevenue.class),
                        primitive(QueryExpenses.class)
                ),
                primitive(WriteSummary.class),
                compound("Generate Charts",
                        primitive(CreateRevenueChart.class),
                        primitive(CreateExpenseChart.class)
                ),
                primitive(CompileAndFormat.class)
        );

        UntypedAgent agent = AgenticServices.plannerBuilder()
                .subAgents(
                        new QueryRevenue(), new QueryExpenses(), new WriteSummary(),
                        new CreateRevenueChart(), new CreateExpenseChart(), new CompileAndFormat())
                .planner(() -> new HtnPlanner(tree))
                .name("htn-test")
                .build();

        agent.invoke(Map.of());

        assertThat(executionOrder).containsExactly(
                "queryRevenue", "queryExpenses", "writeSummary",
                "createRevenueChart", "createExpenseChart", "compileAndFormat");
    }

    @Test
    void dynamic_decomposition() {
        executionOrder.clear();

        TaskNode tree = compound("root",
                primitive(FetchData.class),
                compound("process",
                        (scope, subagents) -> primitives(subagents.get(Transform.class), subagents.get(Validate.class))),
                primitive(Store.class)
        );

        UntypedAgent agent = AgenticServices.plannerBuilder()
                .subAgents(new FetchData(), new Transform(), new Validate(), new Store())
                .planner(() -> new HtnPlanner(tree))
                .name("htn-dynamic")
                .build();

        agent.invoke(Map.of());

        assertThat(executionOrder).containsExactly("fetchData", "transform", "validate", "store");
    }

    @Test
    void conditional_decomposition_detailed_path() {
        executionOrder.clear();

        TaskNode tree = compound("root",
                primitive(CheckConfigDetailed.class,
                        scope -> scope.writeState("useDetailed", true)),
                compound("process",
                        decompose(
                                scope -> scope.readState("useDetailed", false),
                                primitives(DetailedAnalysis.class, DetailedReport.class)),
                        decompose(
                                primitive(QuickSummary.class))),
                primitive(Finalize.class)
        );

        UntypedAgent agent = AgenticServices.plannerBuilder()
                .subAgents(new CheckConfigDetailed(), new DetailedAnalysis(),
                        new DetailedReport(), new QuickSummary(), new Finalize())
                .planner(() -> new HtnPlanner(tree))
                .name("htn-conditional")
                .build();

        agent.invoke(Map.of());

        assertThat(executionOrder).containsExactly("checkConfig", "detailedAnalysis", "detailedReport", "finalize");
    }

    @Test
    void conditional_decomposition_quick_path() {
        executionOrder.clear();

        TaskNode tree = compound("root",
                primitive(CheckConfigSimple.class,
                        scope -> scope.writeState("useDetailed", false)),
                compound("process",
                        decompose(
                                scope -> scope.readState("useDetailed", false),
                                primitives(DetailedAnalysis.class, DetailedReport.class)),
                        decompose(
                                primitive(QuickSummary.class))),
                primitive(Finalize.class)
        );

        UntypedAgent agent = AgenticServices.plannerBuilder()
                .subAgents(new CheckConfigSimple(), new DetailedAnalysis(),
                        new DetailedReport(), new QuickSummary(), new Finalize())
                .planner(() -> new HtnPlanner(tree))
                .name("htn-conditional-2")
                .build();

        agent.invoke(Map.of());

        assertThat(executionOrder).containsExactly("checkConfig", "quickSummary", "finalize");
    }

    @Test
    void preconditions_and_effects() {
        executionOrder.clear();

        TaskNode tree = compound("Process Order",
                primitive(ValidateOrder.class,
                        scope -> scope.writeState("validated", "true")),
                primitive(ProcessPayment.class,
                        scope -> "true".equals(scope.readState("validated", "")),
                        scope -> scope.writeState("paid", "true")),
                primitive(ShipOrder.class,
                        scope -> "true".equals(scope.readState("paid", "")),
                        null)
        );

        UntypedAgent agent = AgenticServices.plannerBuilder()
                .subAgents(new ValidateOrder(), new ProcessPayment(), new ShipOrder())
                .planner(() -> new HtnPlanner(tree))
                .name("htn-effects")
                .build();

        agent.invoke(Map.of());

        assertThat(executionOrder).containsExactly("validateOrder", "processPayment", "shipOrder");
    }

    @Test
    void preconditions_and_effects_digital_order() {
        executionOrder.clear();

        TaskNode tree = compound("Process Order",
                primitive(ValidateOrder.class,
                        scope -> scope.writeState("orderType", "DIGITAL")),
                compound("fulfill",
                        decompose(
                                scope -> scope.readState("orderType", "").contains("DIGITAL"),
                                primitive(SendDownloadLink.class)),
                        decompose(
                                scope -> scope.readState("orderType", "").contains("PHYSICAL"),
                                primitives(CheckInventory.class, ShipOrder.class)),
                        decompose(
                                primitive(ManualReview.class))),
                primitive(SendConfirmation.class,
                        scope -> scope.hasState("orderType"), null)
        );

        UntypedAgent agent = AgenticServices.plannerBuilder()
                .subAgents(new ValidateOrder(), new SendDownloadLink(), new CheckInventory(),
                        new ShipOrder(), new ManualReview(), new SendConfirmation())
                .planner(() -> new HtnPlanner(tree))
                .name("htn-order-digital")
                .build();

        agent.invoke(Map.of());

        assertThat(executionOrder).containsExactly("validateOrder", "sendDownloadLink", "sendConfirmation");
    }

    @Test
    void preconditions_and_effects_physical_order() {
        executionOrder.clear();

        TaskNode tree = compound("Process Order",
                primitive(ValidateOrder.class,
                        scope -> scope.writeState("orderType", "PHYSICAL")),
                compound("fulfill",
                        decompose(
                                scope -> scope.readState("orderType", "").contains("DIGITAL"),
                                primitive(SendDownloadLink.class)),
                        decompose(
                                scope -> scope.readState("orderType", "").contains("PHYSICAL"),
                                primitives(CheckInventory.class, ShipOrder.class)),
                        decompose(
                                primitive(ManualReview.class))),
                primitive(SendConfirmation.class,
                        scope -> scope.hasState("orderType"), null)
        );

        UntypedAgent agent = AgenticServices.plannerBuilder()
                .subAgents(new ValidateOrder(), new SendDownloadLink(), new CheckInventory(),
                        new ShipOrder(), new ManualReview(), new SendConfirmation())
                .planner(() -> new HtnPlanner(tree))
                .name("htn-order-physical")
                .build();

        agent.invoke(Map.of());

        assertThat(executionOrder).containsExactly("validateOrder", "checkInventory", "shipOrder", "sendConfirmation");
    }

    @Test
    void crash_recovery() {
        executionOrder.clear();

        TaskNode tree = compound("root",
                primitive(Step1.class),
                primitive(Step2.class),
                primitive(Step3.class)
        );

        UntypedAgent agent = AgenticServices.plannerBuilder()
                .subAgents(new Step1(), new Step2(), new Step3())
                .planner(() -> new HtnPlanner(tree))
                .name("htn-recovery")
                .build();

        agent.invoke(Map.of());
        assertThat(executionOrder).containsExactly("step1", "step2", "step3");

        executionOrder.clear();
        HtnPlanner recovered = new HtnPlanner(tree);

        List<AgentInstance> subagents = List.of(
                agentInstance("step1", Step1.class), agentInstance("step2", Step2.class), agentInstance("step3", Step3.class));
        recovered.init(new InitPlanningContext(null, null, subagents));
        recovered.restoreExecutionState(Map.of("completed", List.of("step1#0")));

        assertThat(recovered.executionState().get("completed")).isEqualTo(List.of("step1#0"));
    }

    @Test
    void validation_unknown_agent() {
        TaskNode tree = compound("root",
                primitive(Step1.class),
                primitive(NonExistentAgent.class)
        );

        assertThatThrownBy(() -> AgenticServices.plannerBuilder()
                .subAgents(new Step1())
                .planner(() -> new HtnPlanner(tree))
                .name("htn-validation")
                .build()
                .invoke(Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NonExistentAgent");
    }

    @Test
    void single_primitive_task() {
        executionOrder.clear();

        UntypedAgent agent = AgenticServices.plannerBuilder()
                .subAgents(new Step1())
                .planner(() -> new HtnPlanner(primitive(Step1.class)))
                .name("htn-single")
                .build();

        agent.invoke(Map.of());

        assertThat(executionOrder).containsExactly("step1");
    }

    @Test
    void duplicate_agent_names() {
        executionOrder.clear();

        TaskNode tree = compound("root",
                primitive(Step1.class),
                primitive(Step1.class),
                primitive(Step1.class)
        );

        UntypedAgent agent = AgenticServices.plannerBuilder()
                .subAgents(new Step1())
                .planner(() -> new HtnPlanner(tree))
                .name("htn-duplicate")
                .build();

        agent.invoke(Map.of());

        assertThat(executionOrder).containsExactly("step1", "step1", "step1");
    }

    @Test
    void deeply_nested_tree() {
        executionOrder.clear();

        TaskNode tree = compound("level1",
                compound("level2",
                        compound("level3",
                                compound("level4",
                                        primitive(Step1.class)
                                ),
                                primitive(Step2.class)
                        ),
                        primitive(Step3.class)
                ),
                primitive(Finalize.class)
        );

        UntypedAgent agent = AgenticServices.plannerBuilder()
                .subAgents(new Step1(), new Step2(), new Step3(), new Finalize())
                .planner(() -> new HtnPlanner(tree))
                .name("htn-deep")
                .build();

        agent.invoke(Map.of());

        assertThat(executionOrder).containsExactly("step1", "step2", "step3", "finalize");
    }

    @Test
    void empty_dynamic_decomposition() {
        executionOrder.clear();

        TaskNode tree = compound("root",
                primitive(Step1.class),
                compound("empty", (scope, subagents) -> List.of()),
                primitive(Step2.class)
        );

        UntypedAgent agent = AgenticServices.plannerBuilder()
                .subAgents(new Step1(), new Step2())
                .planner(() -> new HtnPlanner(tree))
                .name("htn-empty")
                .build();

        agent.invoke(Map.of());

        assertThat(executionOrder).containsExactly("step1", "step2");
    }

    @Test
    void data_driven_dynamic_decomposition() {
        executionOrder.clear();

        TaskNode tree = compound("batch-pipeline",
                primitive(Scan.class),
                compound("process-all", (scope, subagents) -> {
                    @SuppressWarnings("unchecked")
                    List<String> items = (List<String>) scope.readState("items");
                    if (items == null) {
                        return List.of();
                    }
                    List<TaskNode> perItem = new ArrayList<>();
                    for (String item : items) {
                        perItem.add(compound("handle-" + item,
                                primitive(ValidateItem.class),
                                primitive(ProcessItem.class)));
                    }
                    return perItem;
                }),
                primitive(Report.class)
        );

        UntypedAgent agent = AgenticServices.plannerBuilder()
                .subAgents(new Scan(), new ValidateItem(), new ProcessItem(), new Report())
                .planner(() -> new HtnPlanner(tree))
                .name("htn-batch")
                .build();

        agent.invoke(Map.of());

        assertThat(executionOrder).containsExactly(
                "scan",
                "validate", "process",
                "validate", "process",
                "validate", "process",
                "report");
    }

    // --- Agent helper ---

    private static AgentInstance agentInstance(String name, Class<?> type) {
        return new AgentInstance() {
            @Override public Class<?> type() { return type; }
            @Override public Class<? extends dev.langchain4j.agentic.planner.Planner> plannerType() { return null; }
            @Override public String name() { return name; }
            @Override public String agentId() { return name; }
            @Override public String description() { return ""; }
            @Override public java.lang.reflect.Type outputType() { return String.class; }
            @Override public String outputKey() { return name; }
            @Override public boolean async() { return false; }
            @Override public List<dev.langchain4j.agentic.planner.AgentArgument> arguments() { return List.of(); }
            @Override public AgentInstance parent() { return null; }
            @Override public List<AgentInstance> subagents() { return List.of(); }
            @Override public dev.langchain4j.agentic.planner.AgenticSystemTopology topology() { return dev.langchain4j.agentic.planner.AgenticSystemTopology.SEQUENCE; }
        };
    }

    // --- Agent classes ---

    private static void record(String name, AgenticScope scope) {
        executionOrder.add(name);
    }

    public static class QueryRevenue {
        @Agent(name = "queryRevenue")
        public void run(AgenticScope scope) { record("queryRevenue", scope); }
    }

    public static class QueryExpenses {
        @Agent(name = "queryExpenses")
        public void run(AgenticScope scope) { record("queryExpenses", scope); }
    }

    public static class WriteSummary {
        @Agent(name = "writeSummary")
        public void run(AgenticScope scope) { record("writeSummary", scope); }
    }

    public static class CreateRevenueChart {
        @Agent(name = "createRevenueChart")
        public void run(AgenticScope scope) { record("createRevenueChart", scope); }
    }

    public static class CreateExpenseChart {
        @Agent(name = "createExpenseChart")
        public void run(AgenticScope scope) { record("createExpenseChart", scope); }
    }

    public static class CompileAndFormat {
        @Agent(name = "compileAndFormat")
        public void run(AgenticScope scope) { record("compileAndFormat", scope); }
    }

    public static class FetchData {
        @Agent(name = "fetchData")
        public void run(AgenticScope scope) {
            record("fetchData", scope);
            scope.writeState("data", "raw-data");
        }
    }

    public static class Transform {
        @Agent(name = "transform")
        public void run(AgenticScope scope) { record("transform", scope); }
    }

    public static class Validate {
        @Agent(name = "validate")
        public void run(AgenticScope scope) { record("validate", scope); }
    }

    public static class Store {
        @Agent(name = "store")
        public void run(AgenticScope scope) { record("store", scope); }
    }

    public static class CheckConfigDetailed {
        @Agent(name = "checkConfig")
        public void run(AgenticScope scope) {
            record("checkConfig", scope);
            scope.writeState("useDetailed", true);
        }
    }

    public static class CheckConfigSimple {
        @Agent(name = "checkConfig")
        public void run(AgenticScope scope) {
            record("checkConfig", scope);
            scope.writeState("useDetailed", false);
        }
    }

    public static class DetailedAnalysis {
        @Agent(name = "detailedAnalysis")
        public void run(AgenticScope scope) { record("detailedAnalysis", scope); }
    }

    public static class DetailedReport {
        @Agent(name = "detailedReport")
        public void run(AgenticScope scope) { record("detailedReport", scope); }
    }

    public static class QuickSummary {
        @Agent(name = "quickSummary")
        public void run(AgenticScope scope) { record("quickSummary", scope); }
    }

    public static class Finalize {
        @Agent(name = "finalize")
        public void run(AgenticScope scope) { record("finalize", scope); }
    }

    public static class ValidateOrder {
        @Agent(name = "validateOrder")
        public void run(AgenticScope scope) { record("validateOrder", scope); }
    }

    public static class ProcessPayment {
        @Agent(name = "processPayment")
        public void run(AgenticScope scope) { record("processPayment", scope); }
    }

    public static class ShipOrder {
        @Agent(name = "shipOrder")
        public void run(AgenticScope scope) { record("shipOrder", scope); }
    }

    public static class Scan {
        @Agent(name = "scan")
        public void run(AgenticScope scope) {
            record("scan", scope);
            scope.writeState("items", List.of("invoice-A", "invoice-B", "invoice-C"));
        }
    }

    public static class ValidateItem {
        @Agent(name = "validate")
        public void run(AgenticScope scope) { record("validate", scope); }
    }

    public static class ProcessItem {
        @Agent(name = "process")
        public void run(AgenticScope scope) { record("process", scope); }
    }

    public static class Report {
        @Agent(name = "report")
        public void run(AgenticScope scope) { record("report", scope); }
    }

    public static class Step1 {
        @Agent(name = "step1")
        public void run(AgenticScope scope) { record("step1", scope); }
    }

    public static class Step2 {
        @Agent(name = "step2")
        public void run(AgenticScope scope) { record("step2", scope); }
    }

    public static class Step3 {
        @Agent(name = "step3")
        public void run(AgenticScope scope) { record("step3", scope); }
    }

    public static class SendDownloadLink {
        @Agent(name = "sendDownloadLink")
        public void run(AgenticScope scope) { record("sendDownloadLink", scope); }
    }

    public static class CheckInventory {
        @Agent(name = "checkInventory")
        public void run(AgenticScope scope) { record("checkInventory", scope); }
    }

    public static class ManualReview {
        @Agent(name = "manualReview")
        public void run(AgenticScope scope) { record("manualReview", scope); }
    }

    public static class SendConfirmation {
        @Agent(name = "sendConfirmation")
        public void run(AgenticScope scope) { record("sendConfirmation", scope); }
    }

    public static class NonExistentAgent {
        @Agent(name = "nonExistentAgent")
        public void run(AgenticScope scope) { record("nonExistentAgent", scope); }
    }
}
