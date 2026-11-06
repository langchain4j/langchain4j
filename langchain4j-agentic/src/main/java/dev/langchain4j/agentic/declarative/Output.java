package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as the output definition of a workflow agent,
 * generally combining results from different states of the {@link AgenticScope}.
 * The method must be static on the agent type and return the output of the agent.
 * It can also declare parameters resolved through a {@link SupplierParameterResolver}.
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
 *         @Output
 *         static List<EveningPlan> createPlans(@V("movies") List<String> movies, @V("meals") List<String> meals) {
 *             List<EveningPlan> moviesAndMeals = new ArrayList<>();
 *             for (int i = 0; i < movies.size(); i++) {
 *                 if (i >= meals.size()) {
 *                     break;
 *                 }
 *                 moviesAndMeals.add(new EveningPlan(movies.get(i), meals.get(i)));
 *             }
 *             return moviesAndMeals;
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
public @interface Output {}
