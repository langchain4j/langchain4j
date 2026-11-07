package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.agentic.AgenticServices;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Used in combination with {@link ParallelAgent} or {@link ParallelMapperAgent} to specify the executor that will be used
 * to run the sub-agents in parallel.
 * The method annotated with {@link ParallelExecutor} must be static on the agent type and return an instance of {@link java.util.concurrent.Executor}.
 * It can optionally declare parameters resolved through {@link SupplierParameterResolver}.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface EveningPlannerAgent {
 *
 *         @ParallelAgent(outputKey = "plans",
 *                 subAgents = { FoodExpert.class, MovieExpert.class })
 *         List<EveningPlan> plan(@V("mood") String mood);
 *
 *         @ParallelExecutor
 *         static Executor executor() {
 *             return Executors.newFixedThreadPool(2);
 *         }
 *     }
 * }
 * </pre>
 * <p>
 * Like every helper method, it can be an instance method of a helper object of the agent instead of a static method
 * of the agent type: see the "Helper objects" section of the agentic tutorial and
 * {@link AgenticServices.AgentConfigurator}.
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface ParallelExecutor {}
