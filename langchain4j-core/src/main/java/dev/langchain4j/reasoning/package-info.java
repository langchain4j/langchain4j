/**
 * ReasoningBank: A memory framework for agent self-improvement through accumulated reasoning experiences.
 * <p>
 * This package provides components for storing, retrieving, and applying reasoning strategies
 * learned from past task executions. It enables agents to evolve by learning from both
 * successful and failed experiences.
 * <p>
 * Key components:
 * <ul>
 *   <li>{@link dev.langchain4j.reasoning.ReasoningTrace} - Captures raw reasoning from task execution</li>
 *   <li>{@link dev.langchain4j.reasoning.ReasoningStrategy} - Generalizable strategy distilled from traces</li>
 *   <li>{@link dev.langchain4j.reasoning.ReasoningBank} - Storage interface for strategies</li>
 *   <li>{@link dev.langchain4j.reasoning.ReasoningDistiller} - Transforms traces into strategies</li>
 *   <li>{@link dev.langchain4j.reasoning.ReasoningAugmentor} - Augments prompts with relevant strategies</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create a reasoning bank
 * ReasoningBank bank = new InMemoryReasoningBank();
 *
 * // Store a strategy
 * ReasoningStrategy strategy = ReasoningStrategy.builder()
 *     .taskPattern("Mathematical problem solving")
 *     .strategy("Break down complex problems into smaller steps")
 *     .pitfallsToAvoid("Don't skip verification of intermediate results")
 *     .confidenceScore(0.8)
 *     .build();
 * Embedding embedding = embeddingModel.embed(strategy.taskPattern()).content();
 * bank.store(strategy, embedding);
 *
 * // Create an augmentor
 * ReasoningAugmentor augmentor = ReasoningAugmentor.builder()
 *     .reasoningBank(bank)
 *     .embeddingModel(embeddingModel)
 *     .maxStrategies(3)
 *     .build();
 *
 * // Augment a user message
 * UserMessage userMessage = UserMessage.from("Solve this calculus problem...");
 * ReasoningAugmentationResult result = augmentor.augment(userMessage);
 * ChatMessage augmentedMessage = result.augmentedMessage();
 * }</pre>
 * <p>
 * This implementation is inspired by the ReasoningBank paper: "Scaling Agent Self-Evolving with Reasoning Memory"
 * which proposes a memory framework that extracts generalizable reasoning strategies from agent experiences.
 *
 * @since 1.11.0
 */
@Experimental
package dev.langchain4j.reasoning;

import dev.langchain4j.Experimental;
