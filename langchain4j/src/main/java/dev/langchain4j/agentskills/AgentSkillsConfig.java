package dev.langchain4j.agentskills;

import dev.langchain4j.Experimental;
import dev.langchain4j.agentskills.execution.ScriptExecutor;

import java.nio.file.Path;

/**
 * Configuration for Agent Skills functionality.
 * <p>
 * This class encapsulates all Agent Skills related settings:
 * <ul>
 *   <li>Skills provider - where to load skills from</li>
 *   <li>Script executor - how to execute skill scripts</li>
 *   <li>Max iterations - how many times the AI can use skills in one invocation</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * AgentSkillsProvider provider = DefaultAgentSkillsProvider.builder()
 *     .skillDirectories(Path.of("/my-skills"))
 *     .build();
 *
 * AgentSkillsConfig config = AgentSkillsConfig.builder()
 *     .skillsProvider(provider)
 *     .scriptExecutor(new DefaultScriptExecutor(120)) // 120s timeout
 *     .maxIterations(15)
 *     .build();
 *
 * Assistant assistant = AiServices.builder(Assistant.class)
 *     .chatModel(chatModel)
 *     .agentSkillsConfig(config)
 *     .build();
 * }</pre>
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 * @since 1.12.0
 */
@Experimental
public class AgentSkillsConfig {

    private final AgentSkillsProvider skillsProvider;
    private final ScriptExecutor scriptExecutor;
    private final Integer maxIterations;

    private AgentSkillsConfig(Builder builder) {
        this.skillsProvider = builder.skillsProvider;
        this.scriptExecutor = builder.scriptExecutor;
        this.maxIterations = builder.maxIterations;
    }

    /**
     * Returns the skills provider.
     *
     * @return the skills provider, or null if not configured
     */
    public AgentSkillsProvider skillsProvider() {
        return skillsProvider;
    }

    /**
     * Returns the script executor.
     *
     * @return the script executor, or null if not configured (will use default)
     */
    public ScriptExecutor scriptExecutor() {
        return scriptExecutor;
    }

    /**
     * Returns the maximum number of skill iterations.
     *
     * @return the max iterations, or null if not configured (will use default)
     */
    public Integer maxIterations() {
        return maxIterations;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private AgentSkillsProvider skillsProvider;
        private ScriptExecutor scriptExecutor;
        private Integer maxIterations;

        /**
         * Sets the skills provider.
         * <p>
         * The provider is responsible for loading and managing skills.
         * Use {@link DefaultAgentSkillsProvider} to load skills from file system.
         *
         * @param skillsProvider the skills provider
         * @return this builder
         */
        public Builder skillsProvider(AgentSkillsProvider skillsProvider) {
            this.skillsProvider = skillsProvider;
            return this;
        }

        /**
         * Sets the script executor.
         * <p>
         * The executor is responsible for running skill scripts.
         * If not set, a {@link dev.langchain4j.agentskills.execution.DefaultScriptExecutor}
         * with 60s timeout will be used.
         *
         * @param scriptExecutor the script executor
         * @return this builder
         */
        public Builder scriptExecutor(ScriptExecutor scriptExecutor) {
            this.scriptExecutor = scriptExecutor;
            return this;
        }

        /**
         * Sets the maximum number of skill instruction iterations.
         * <p>
         * This limits how many times the AI can request skill operations in a single invocation.
         * If not set, defaults to 10.
         *
         * @param maxIterations the maximum iterations (must be at least 1)
         * @return this builder
         */
        public Builder maxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        public AgentSkillsConfig build() {
            return new AgentSkillsConfig(this);
        }
    }
}
