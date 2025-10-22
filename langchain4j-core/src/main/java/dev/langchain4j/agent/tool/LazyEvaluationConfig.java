package dev.langchain4j.agent.tool;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Configuration for controlling lazy evaluation behavior of tool execution results.
 * <p>
 * This configuration allows fine-grained control over when tool execution results
 * are evaluated during AI service interactions. It supports different evaluation modes,
 * explicit tool overrides, and performance monitoring capabilities.
 * </p>
 * 
 * <h3>Usage Examples:</h3>
 * <pre>{@code
 * // Basic configuration with AUTO mode
 * LazyEvaluationConfig config = LazyEvaluationConfig.builder()
 *     .mode(LazyEvaluationMode.AUTO)
 *     .build();
 * 
 * // Configuration with explicit tool overrides
 * LazyEvaluationConfig config = LazyEvaluationConfig.builder()
 *     .mode(LazyEvaluationMode.ENABLED)
 *     .addLazyTool("expensiveCalculation")
 *     .addEagerTool("criticalValidation")
 *     .enablePerformanceMonitoring(true)
 *     .build();
 * 
 * // Check if a tool should use lazy evaluation
 * boolean shouldBeLazy = config.shouldUseLazyEvaluation("myTool");
 * }</pre>
 */
public final class LazyEvaluationConfig {
    
    /**
     * Default evaluation mode for backward compatibility.
     */
    public static final LazyEvaluationMode DEFAULT_MODE = LazyEvaluationMode.DISABLED;
    
    /**
     * Default performance monitoring setting.
     */
    public static final boolean DEFAULT_PERFORMANCE_MONITORING = false;
    
    private final LazyEvaluationMode mode;
    private final Set<String> lazyTools;
    private final Set<String> eagerTools;
    private final boolean performanceMonitoringEnabled;
    
    private LazyEvaluationConfig(Builder builder) {
        this.mode = ensureNotNull(builder.mode, "mode");
        this.lazyTools = Collections.unmodifiableSet(new HashSet<>(builder.lazyTools));
        this.eagerTools = Collections.unmodifiableSet(new HashSet<>(builder.eagerTools));
        this.performanceMonitoringEnabled = builder.performanceMonitoringEnabled;
        
        validateConfiguration();
    }
    
    /**
     * Gets the lazy evaluation mode.
     * 
     * @return the evaluation mode
     */
    public LazyEvaluationMode mode() {
        return mode;
    }
    
    /**
     * Gets the set of tools explicitly configured for lazy evaluation.
     * 
     * @return an unmodifiable set of tool names configured for lazy evaluation
     */
    public Set<String> lazyTools() {
        return lazyTools;
    }
    
    /**
     * Gets the set of tools explicitly configured for eager evaluation.
     * 
     * @return an unmodifiable set of tool names configured for eager evaluation
     */
    public Set<String> eagerTools() {
        return eagerTools;
    }
    
    /**
     * Checks if performance monitoring is enabled.
     * 
     * @return true if performance monitoring is enabled, false otherwise
     */
    public boolean isPerformanceMonitoringEnabled() {
        return performanceMonitoringEnabled;
    }
    
    /**
     * Determines whether a specific tool should use lazy evaluation based on the configuration.
     * <p>
     * The decision logic follows this priority order:
     * <ol>
     *   <li>If the tool is explicitly in the eager tools set, return false</li>
     *   <li>If the tool is explicitly in the lazy tools set, return true</li>
     *   <li>Apply the mode-specific logic</li>
     * </ol>
     * 
     * @param toolName the name of the tool to check (must not be null)
     * @return true if the tool should use lazy evaluation, false otherwise
     * @throws IllegalArgumentException if toolName is null
     */
    public boolean shouldUseLazyEvaluation(String toolName) {
        ensureNotNull(toolName, "toolName");
        
        // Explicit overrides take precedence
        if (eagerTools.contains(toolName)) {
            return false;
        }
        if (lazyTools.contains(toolName)) {
            return true;
        }
        
        // Apply mode-specific logic
        return switch (mode) {
            case DISABLED -> false;
            case IMMEDIATE_ONLY -> false;
            case ENABLED -> true;
            case AUTO -> determineAutoEvaluation(toolName);
        };
    }
    
    /**
     * Creates a new builder for constructing LazyEvaluationConfig instances.
     * 
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates a default configuration with DISABLED mode for backward compatibility.
     * 
     * @return a default configuration instance
     */
    public static LazyEvaluationConfig defaultConfig() {
        return builder().build();
    }
    
    private void validateConfiguration() {
        // Check for tools that are in both lazy and eager sets
        Set<String> intersection = new HashSet<>(lazyTools);
        intersection.retainAll(eagerTools);
        if (!intersection.isEmpty()) {
            throw new IllegalArgumentException(
                "Tools cannot be configured as both lazy and eager: " + intersection
            );
        }
        
        // Validate mode-specific constraints
        if (mode == LazyEvaluationMode.IMMEDIATE_ONLY && !lazyTools.isEmpty()) {
            throw new IllegalArgumentException(
                "IMMEDIATE_ONLY mode cannot have explicit lazy tools configured"
            );
        }
    }
    
    private boolean determineAutoEvaluation(String toolName) {
        // Auto mode logic - this could be enhanced with more sophisticated heuristics
        // For now, we use a simple heuristic based on tool name patterns
        
        // Tools with certain patterns are considered expensive and good candidates for lazy evaluation
        String lowerToolName = toolName.toLowerCase();
        return lowerToolName.contains("expensive") ||
               lowerToolName.contains("slow") ||
               lowerToolName.contains("heavy") ||
               lowerToolName.contains("complex") ||
               lowerToolName.contains("calculation") ||
               lowerToolName.contains("analysis");
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LazyEvaluationConfig that = (LazyEvaluationConfig) o;
        return performanceMonitoringEnabled == that.performanceMonitoringEnabled &&
               mode == that.mode &&
               Objects.equals(lazyTools, that.lazyTools) &&
               Objects.equals(eagerTools, that.eagerTools);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(mode, lazyTools, eagerTools, performanceMonitoringEnabled);
    }
    
    @Override
    public String toString() {
        return "LazyEvaluationConfig{" +
               "mode=" + mode +
               ", lazyTools=" + lazyTools +
               ", eagerTools=" + eagerTools +
               ", performanceMonitoringEnabled=" + performanceMonitoringEnabled +
               '}';
    }
    
    /**
     * Builder for constructing LazyEvaluationConfig instances.
     * <p>
     * This builder follows the builder pattern and provides a fluent API
     * for configuring lazy evaluation behavior.
     * </p>
     */
    public static final class Builder {
        private LazyEvaluationMode mode = DEFAULT_MODE;
        private final Set<String> lazyTools = new HashSet<>();
        private final Set<String> eagerTools = new HashSet<>();
        private boolean performanceMonitoringEnabled = DEFAULT_PERFORMANCE_MONITORING;
        
        private Builder() {
            // Private constructor to enforce use of static factory method
        }
        
        /**
         * Sets the lazy evaluation mode.
         * 
         * @param mode the evaluation mode (must not be null)
         * @return this builder instance for method chaining
         * @throws IllegalArgumentException if mode is null
         */
        public Builder mode(LazyEvaluationMode mode) {
            this.mode = ensureNotNull(mode, "mode");
            return this;
        }
        
        /**
         * Adds a tool to the set of tools that should use lazy evaluation.
         * 
         * @param toolName the name of the tool (must not be null or empty)
         * @return this builder instance for method chaining
         * @throws IllegalArgumentException if toolName is null or empty
         */
        public Builder addLazyTool(String toolName) {
            ensureNotNull(toolName, "toolName");
            if (toolName.trim().isEmpty()) {
                throw new IllegalArgumentException("Tool name cannot be empty");
            }
            this.lazyTools.add(toolName);
            return this;
        }
        
        /**
         * Adds multiple tools to the set of tools that should use lazy evaluation.
         * 
         * @param toolNames the names of the tools (must not be null, and individual names must not be null or empty)
         * @return this builder instance for method chaining
         * @throws IllegalArgumentException if toolNames is null or contains null/empty names
         */
        public Builder addLazyTools(String... toolNames) {
            ensureNotNull(toolNames, "toolNames");
            for (String toolName : toolNames) {
                addLazyTool(toolName);
            }
            return this;
        }
        
        /**
         * Adds a tool to the set of tools that should use eager evaluation.
         * 
         * @param toolName the name of the tool (must not be null or empty)
         * @return this builder instance for method chaining
         * @throws IllegalArgumentException if toolName is null or empty
         */
        public Builder addEagerTool(String toolName) {
            ensureNotNull(toolName, "toolName");
            if (toolName.trim().isEmpty()) {
                throw new IllegalArgumentException("Tool name cannot be empty");
            }
            this.eagerTools.add(toolName);
            return this;
        }
        
        /**
         * Adds multiple tools to the set of tools that should use eager evaluation.
         * 
         * @param toolNames the names of the tools (must not be null, and individual names must not be null or empty)
         * @return this builder instance for method chaining
         * @throws IllegalArgumentException if toolNames is null or contains null/empty names
         */
        public Builder addEagerTools(String... toolNames) {
            ensureNotNull(toolNames, "toolNames");
            for (String toolName : toolNames) {
                addEagerTool(toolName);
            }
            return this;
        }
        
        /**
         * Sets whether performance monitoring should be enabled.
         * 
         * @param enabled true to enable performance monitoring, false to disable
         * @return this builder instance for method chaining
         */
        public Builder enablePerformanceMonitoring(boolean enabled) {
            this.performanceMonitoringEnabled = enabled;
            return this;
        }
        
        /**
         * Builds the LazyEvaluationConfig instance.
         * 
         * @return a new LazyEvaluationConfig instance
         * @throws IllegalArgumentException if the configuration is invalid
         */
        public LazyEvaluationConfig build() {
            return new LazyEvaluationConfig(this);
        }
    }
}