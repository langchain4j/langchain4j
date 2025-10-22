package dev.langchain4j.agent.tool;

/**
 * Defines the modes for lazy evaluation of tool execution results.
 * <p>
 * This enum controls when and how tool execution results are evaluated during AI service interactions.
 * Different modes provide varying levels of performance optimization and control over tool execution timing.
 * </p>
 */
public enum LazyEvaluationMode {
    
    /**
     * Lazy evaluation is completely disabled.
     * <p>
     * All tool execution results are evaluated immediately when tools are executed.
     * This is the most conservative mode that maintains traditional behavior.
     * </p>
     */
    DISABLED,
    
    /**
     * Only immediate evaluation is allowed.
     * <p>
     * Tools marked for immediate evaluation will be executed immediately,
     * while others will be deferred. This mode provides selective control
     * over which tools should be evaluated immediately.
     * </p>
     */
    IMMEDIATE_ONLY,
    
    /**
     * Lazy evaluation is fully enabled.
     * <p>
     * Tool execution results are deferred until actually needed,
     * providing maximum performance benefits through lazy evaluation.
     * Tools can still be explicitly marked for immediate evaluation.
     * </p>
     */
    ENABLED,
    
    /**
     * Automatic mode that intelligently decides when to use lazy evaluation.
     * <p>
     * The system automatically determines the best evaluation strategy
     * based on factors such as tool complexity, execution context,
     * and performance characteristics. This mode balances performance
     * optimization with execution reliability.
     * </p>
     */
    AUTO
}