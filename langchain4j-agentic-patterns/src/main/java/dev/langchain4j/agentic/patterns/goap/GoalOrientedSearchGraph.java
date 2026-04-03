package dev.langchain4j.agentic.patterns.goap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import dev.langchain4j.agentic.patterns.goap.DependencyGraphSearch.Node;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoalOrientedSearchGraph {

    private static final Logger LOG = LoggerFactory.getLogger(GoalOrientedSearchGraph.class);

    private record NodePair(Node input, Node output) { }

    private final Map<String, Node> nodes = new HashMap<>();
    private final Map<NodePair, AgentInstance> edges = new HashMap<>();

    public GoalOrientedSearchGraph(List<AgentInstance> agents) {
        init(agents);
    }

    private void init(List<AgentInstance> agents) {
        for (AgentInstance agent : agents) {
            List<Node> inputs = agent.arguments().stream()
                    .map(AgentArgument::name)
                    .map(arg -> nodes.computeIfAbsent(arg, Node::new))
                    .toList();
            Node output = nodes.computeIfAbsent(agent.outputKey(), Node::new);

            inputs.forEach(input -> {
                input.addOutput(output);
                edges.put(new NodePair(input, output), agent);
            });
        }
    }

    public List<AgentInstance> search(Collection<String> preconditions, String goal) {
        List<Node> nodesPath = DependencyGraphSearch.search(nodes.get(goal), preconditions.stream().map(nodes::get).toList());

        if (nodesPath == null) {
            return List.of();
        }

        int preconditionsCounter = 0;
        List<AgentInstance> agentsPath = new ArrayList<>();
        for (int i = 1; i < nodesPath.size(); i++) {
            Node output = nodesPath.get(i);
            if (preconditions.contains(output.getId())) {
                preconditionsCounter++;
                continue;
            }
            for (int j = i -1; j >= 0; j--) {
                AgentInstance agent = edges.get(new NodePair(nodesPath.get(j), output));
                if (agent != null) {
                    agentsPath.add(agent);
                    break;
                }
            }
            if (agentsPath.size() != i - preconditionsCounter) {
                throw new IllegalStateException("No path found for node: " + output.getId());
            }
        }

        LOG.info("Agents path sequence: {}", agentsPath.stream().map(AgentInstance::name).toList());

        return agentsPath;
    }
}
