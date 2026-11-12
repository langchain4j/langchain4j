package dev.langchain4j.agentic.patterns.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.planner.AgentInstance;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The names under which the decision planners offer their subagents to a decision model, as question or option names.
 */
@Internal
public final class DecisionQuestionNames {

    private DecisionQuestionNames() {}

    /**
     * Returns the name under which each subagent is offered to the decision model, keyed like the given subagents.
     * <p>
     * A subagent keyed by its name is offered under its name, as it is. A subagent keyed by its id, because several
     * subagents have its name, is offered under its id with each {@code $} replaced by {@code _}, for example
     * {@code medical_0} for {@code medical$0}, since some decision models, such as Gemini, accept only
     * {@code [A-Za-z0-9_-]} in question names. If that name is already taken, by a reserved name, by the name of
     * another subagent or by the name given to another subagent before it, {@code _1}, {@code _2}... is appended until
     * it is free, in the order of the subagents.
     *
     * @param subagents the subagents, keyed by their name, or by their id when several subagents have their name.
     * @param reservedNames the names of the other questions of the decision request.
     */
    public static Map<String, String> of(Map<String, AgentInstance> subagents, Set<String> reservedNames) {
        Set<String> taken = new HashSet<>(reservedNames);
        subagents.forEach((key, agent) -> {
            if (key.equals(agent.name())) {
                taken.add(key);
            }
        });
        Map<String, String> names = new LinkedHashMap<>();
        subagents.forEach((key, agent) -> {
            if (key.equals(agent.name())) {
                names.put(key, key);
                return;
            }
            String base = key.replace('$', '_');
            String name = base;
            for (int i = 1; taken.contains(name); i++) {
                name = base + "_" + i;
            }
            taken.add(name);
            names.put(key, name);
        });
        return names;
    }
}
