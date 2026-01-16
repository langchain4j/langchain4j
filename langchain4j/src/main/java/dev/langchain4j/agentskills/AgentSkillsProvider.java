package dev.langchain4j.agentskills;

import dev.langchain4j.Experimental;

import java.util.List;

/**
 * Provider interface for Agent Skills.
 * <p>
 * Implementations of this interface are responsible for discovering, loading,
 * and providing skills to the AI service. This is similar to {@code ToolProvider}
 * but for Agent Skills.
 * <p>
 * Agent Skills follow the specification from <a href="https://agentskills.io">agentskills.io</a>.
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 * @since 1.12.0
 */
@Experimental
@FunctionalInterface
public interface AgentSkillsProvider {

    /**
     * Provides skills for the current request context.
     *
     * @param request the request containing context information
     * @return the result containing available skills
     */
    AgentSkillsProviderResult provideSkills(AgentSkillsProviderRequest request);

    /**
     * Returns all available skills.
     * <p>
     * Default implementation calls {@link #provideSkills(AgentSkillsProviderRequest)}
     * with a default request.
     *
     * @return list of all skills
     */
    default List<Skill> allSkills() {
        return provideSkills(AgentSkillsProviderRequest.builder().build()).skills();
    }

    /**
     * Returns a skill by its name.
     *
     * @param name the skill name
     * @return the skill, or null if not found
     */
    default Skill skillByName(String name) {
        if (name == null) {
            return null;
        }
        return allSkills().stream()
                .filter(s -> name.equals(s.name()))
                .findFirst()
                .orElse(null);
    }
}
