package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as the output definition of a workflow agent,
 * generally combining results from different states of the {@link AgenticScope}.
 * The method must be static and return the output of the agent.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface EveningPlannerAgent {
 *
 *         @ParallelAgent(outputKey = "plans", subAgents = {
 *                 @SubAgent(type = FoodExpert.class, outputKey = "meals"),
 *                 @SubAgent(type = MovieExpert.class, outputKey = "movies")
 *         })
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
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface Output {}
