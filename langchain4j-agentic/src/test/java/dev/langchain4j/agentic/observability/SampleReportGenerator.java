package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.workflow.ConditionalAgent;
import dev.langchain4j.agentic.workflow.ConditionalAgentInstance;
import dev.langchain4j.agentic.workflow.LoopAgentInstance;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.agentic.observability.HtmlReportGenerator.generateReport;

/**
 * Generates a sample HTML report with mock data to preview the AgenticSystemReport visualization.
 * Run the main method and open the generated sample-report.html in a browser.
 */
public class SampleReportGenerator {

    // Marker interfaces so type().getSimpleName() returns meaningful names
    interface ExpertRouterAgent {}
    interface CategoryRouter {}
    interface MedicalExpert {}
    interface TechnicalExpert {}
    interface StoryWriter {}
    interface StyleScorer {}

    public static void main(String[] args) throws Exception {

        // ----- Build topology: Sequence → classify + Router(medical|technical|legal(loop)) -----

        MockAgent classify = new MockAgent("classify", CategoryRouter.class,
                AgenticSystemTopology.AI_AGENT,
                "Classifies user questions into domain categories",
                List.of(new AgentArgument(String.class, "question")),
                "category", String.class);

        MockAgent medical = new MockAgent("medical", MedicalExpert.class,
                AgenticSystemTopology.AI_AGENT,
                "Provides medical advice and first-aid guidance",
                List.of(new AgentArgument(String.class, "question")),
                "response", String.class);

        MockAgent technical = new MockAgent("technical", TechnicalExpert.class,
                AgenticSystemTopology.AI_AGENT,
                "Provides technical support and troubleshooting",
                List.of(new AgentArgument(String.class, "question")),
                "response", String.class);

        // Legal branch uses a loop: writer + scorer, max 3 iterations
        MockAgent writer = new MockAgent("writer", StoryWriter.class,
                AgenticSystemTopology.AI_AGENT,
                "Drafts a legal response",
                List.of(new AgentArgument(String.class, "question")),
                "response", String.class);

        MockAgent scorer = new MockAgent("scorer", StyleScorer.class,
                AgenticSystemTopology.AI_AGENT,
                "Scores the quality of the legal response",
                List.of(new AgentArgument(String.class, "response")),
                "score", Double.class);

        MockLoopAgent legalLoop = new MockLoopAgent("legalRefine",
                "Iteratively refines the legal response until quality threshold is met",
                List.of(writer, scorer),
                "response", Object.class,
                3, "score greater than 0.8", true);

        MockConditionalAgent router = new MockConditionalAgent("router",
                "Routes to the appropriate domain expert based on category",
                "response", Object.class,
                List.of(
                        new ConditionalAgent("category is medical", null, List.of(medical)),
                        new ConditionalAgent("category is technical", null, List.of(technical)),
                        new ConditionalAgent("category is legal", null, List.of(legalLoop))
                ));

        MockAgent sequence = new MockAgent("ask", ExpertRouterAgent.class,
                AgenticSystemTopology.SEQUENCE,
                null,
                List.of(),
                "response", String.class);
        sequence.subagents = List.of(classify, router);

        // Wire parent references
        classify.parent = sequence;
        router.parent = sequence;
        medical.parent = router;
        technical.parent = router;
        legalLoop.parent = router;
        writer.parent = legalLoop;
        scorer.parent = legalLoop;

        // ----- Create monitor and simulate executions -----

        AgentMonitor monitor = new AgentMonitor();
        monitor.setRootAgent(sequence);

        // Execution 1 (user-alice): medical path
        simulateExecution(monitor, new MockScope("user-alice"),
                sequence, classify, router, medical,
                Map.of("question", "I broke my leg while hiking, what should I do?"),
                "MEDICAL",
                "Seek immediate medical attention. Immobilize the leg and call emergency services.");

        // Execution 2 (user-bob): technical path
        simulateExecution(monitor, new MockScope("user-bob"),
                sequence, classify, router, technical,
                Map.of("question", "My laptop screen flickers after a software update, how to fix?"),
                "TECHNICAL",
                "Roll back the display driver via Device Manager. If that fails, boot in Safe Mode.");

        // Execution 3 (user-alice again): legal path with loop iterations
        simulateLegalExecution(monitor, new MockScope("user-alice"),
                sequence, classify, router, legalLoop, writer, scorer,
                Map.of("question", "Can I sue my neighbor for the hiking trail accident?"),
                "LEGAL",
                List.of("You may have a negligence claim...", "Given the property ownership and duty of care..."),
                List.of(0.6, 0.85),
                "Given the property ownership and duty of care, you likely have grounds for a negligence claim.");

        // ----- Generate report -----

        Path output = Path.of("langchain4j-agentic", "src", "test", "resources", "sample-report.html");
        generateReport(monitor, output);
        System.out.println("Report written to " + output.toAbsolutePath());
    }

    // ---------- Execution simulation helpers ----------

    private static void simulateExecution(AgentMonitor monitor, MockScope scope,
                                          MockAgent sequence, MockAgent classify,
                                          MockAgent router, MockAgent expert,
                                          Map<String, Object> inputs,
                                          String category, String response) throws Exception {
        monitor.beforeAgentInvocation(new AgentRequest(scope, sequence, inputs));
        Thread.sleep(5);

        monitor.beforeAgentInvocation(new AgentRequest(scope, classify, inputs));
        Thread.sleep(35);
        monitor.afterAgentInvocation(new AgentResponse(scope, classify, inputs, category));

        monitor.beforeAgentInvocation(new AgentRequest(scope, router, Map.of("category", category)));
        Thread.sleep(3);

        monitor.beforeAgentInvocation(new AgentRequest(scope, expert, inputs));
        Thread.sleep(55);
        monitor.afterAgentInvocation(new AgentResponse(scope, expert, inputs, response));

        Thread.sleep(2);
        monitor.afterAgentInvocation(new AgentResponse(scope, router, Map.of("category", category), response));

        Thread.sleep(1);
        monitor.afterAgentInvocation(new AgentResponse(scope, sequence, inputs, response));
    }

    private static void simulateLegalExecution(AgentMonitor monitor, MockScope scope,
                                               MockAgent sequence, MockAgent classify,
                                               MockAgent router, MockAgent loop,
                                               MockAgent writer, MockAgent scorer,
                                               Map<String, Object> inputs, String category,
                                               List<String> drafts, List<Double> scores,
                                               String finalResponse) throws Exception {
        monitor.beforeAgentInvocation(new AgentRequest(scope, sequence, inputs));
        Thread.sleep(5);

        monitor.beforeAgentInvocation(new AgentRequest(scope, classify, inputs));
        Thread.sleep(30);
        monitor.afterAgentInvocation(new AgentResponse(scope, classify, inputs, category));

        monitor.beforeAgentInvocation(new AgentRequest(scope, router, Map.of("category", category)));
        Thread.sleep(3);

        monitor.beforeAgentInvocation(new AgentRequest(scope, loop, inputs));
        Thread.sleep(2);

        // Loop iterations
        for (int i = 0; i < drafts.size(); i++) {
            monitor.beforeAgentInvocation(new AgentRequest(scope, writer, inputs));
            Thread.sleep(40);
            monitor.afterAgentInvocation(new AgentResponse(scope, writer, inputs, drafts.get(i)));

            monitor.beforeAgentInvocation(new AgentRequest(scope, scorer,
                    Map.of("response", drafts.get(i))));
            Thread.sleep(20);
            monitor.afterAgentInvocation(new AgentResponse(scope, scorer,
                    Map.of("response", drafts.get(i)), scores.get(i)));
        }

        Thread.sleep(2);
        monitor.afterAgentInvocation(new AgentResponse(scope, loop, inputs, finalResponse));

        Thread.sleep(1);
        monitor.afterAgentInvocation(new AgentResponse(scope, router, Map.of("category", category), finalResponse));

        Thread.sleep(1);
        monitor.afterAgentInvocation(new AgentResponse(scope, sequence, inputs, finalResponse));
    }

    // ---------- Mock implementations ----------

    static class MockAgent implements AgentInstance {
        final String name;
        final Class<?> type;
        final AgenticSystemTopology topology;
        final String description;
        final List<AgentArgument> arguments;
        final String outputKey;
        final Type outputType;
        AgentInstance parent;
        List<AgentInstance> subagents = List.of();

        MockAgent(String name, Class<?> type, AgenticSystemTopology topology,
                  String description, List<AgentArgument> arguments,
                  String outputKey, Type outputType) {
            this.name = name;
            this.type = type;
            this.topology = topology;
            this.description = description;
            this.arguments = arguments;
            this.outputKey = outputKey;
            this.outputType = outputType;
        }

        @Override public Class<?> type() { return type; }
        @Override public Class<? extends Planner> plannerType() { return null; }
        @Override public String name() { return name; }
        @Override public String agentId() { return name; }
        @Override public String description() { return description; }
        @Override public Type outputType() { return outputType; }
        @Override public String outputKey() { return outputKey; }
        @Override public boolean async() { return false; }
        @Override public List<AgentArgument> arguments() { return arguments; }
        @Override public AgentInstance parent() { return parent; }
        @Override public List<AgentInstance> subagents() { return subagents; }
        @Override public AgenticSystemTopology topology() { return topology; }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends AgentInstance> T as(Class<T> cls) {
            if (cls.isInstance(this)) return cls.cast(this);
            throw new ClassCastException("Cannot cast " + name + " to " + cls.getSimpleName());
        }
    }

    static class MockConditionalAgent extends MockAgent implements ConditionalAgentInstance {
        private final List<ConditionalAgent> conditionalSubagents;

        MockConditionalAgent(String name, String description, String outputKey, Type outputType,
                             List<ConditionalAgent> conditionalSubagents) {
            super(name, null, AgenticSystemTopology.ROUTER, description, List.of(), outputKey, outputType);
            this.conditionalSubagents = conditionalSubagents;
            // Flatten all children
            List<AgentInstance> all = new ArrayList<>();
            for (ConditionalAgent ca : conditionalSubagents) {
                all.addAll(ca.agentInstances());
            }
            this.subagents = all;
        }

        @Override
        public List<ConditionalAgent> conditionalSubagents() {
            return conditionalSubagents;
        }
    }

    static class MockLoopAgent extends MockAgent implements LoopAgentInstance {
        private final int maxIterations;
        private final String exitCondition;
        private final boolean testExitAtLoopEnd;

        MockLoopAgent(String name, String description, List<AgentInstance> body,
                      String outputKey, Type outputType,
                      int maxIterations, String exitCondition, boolean testExitAtLoopEnd) {
            super(name, null, AgenticSystemTopology.LOOP, description, List.of(), outputKey, outputType);
            this.subagents = body;
            this.maxIterations = maxIterations;
            this.exitCondition = exitCondition;
            this.testExitAtLoopEnd = testExitAtLoopEnd;
        }

        @Override public int maxIterations() { return maxIterations; }
        @Override public boolean testExitAtLoopEnd() { return testExitAtLoopEnd; }
        @Override public String exitCondition() { return exitCondition; }
    }

    record MockScope(Object memoryId) implements AgenticScope {
        @Override public void writeState(String key, Object value) {}
        @Override public <T> void writeState(Class<? extends TypedKey<T>> key, T value) {}
        @Override public void writeStates(Map<String, Object> newState) {}
        @Override public boolean hasState(String key) { return false; }
        @Override public boolean hasState(Class<? extends TypedKey<?>> key) { return false; }
        @Override public Object readState(String key) { return null; }
        @Override public <T> T readState(String key, T dv) { return dv; }
        @Override public <T> T readState(Class<? extends TypedKey<T>> key) { return null; }
        @Override public Map<String, Object> state() { return Map.of(); }
        @Override public String contextAsConversation(String... n) { return ""; }
        @Override public String contextAsConversation(Object... a) { return ""; }
        @Override public List<dev.langchain4j.agentic.scope.AgentInvocation> agentInvocations() { return List.of(); }
        @Override public List<dev.langchain4j.agentic.scope.AgentInvocation> agentInvocations(String n) { return List.of(); }
        @Override public List<dev.langchain4j.agentic.scope.AgentInvocation> agentInvocations(Class<?> t) { return List.of(); }
    }
}
