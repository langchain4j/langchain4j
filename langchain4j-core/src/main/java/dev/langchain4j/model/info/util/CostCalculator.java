package dev.langchain4j.model.info.util;

import dev.langchain4j.model.info.Cost;
import dev.langchain4j.model.info.ModelInfo;

/**
 * Utility class for calculating costs across different usage patterns.
 */
public class CostCalculator {

    private CostCalculator() {
        // Utility class
    }

    /**
     * Calculate cost for a simple conversation.
     *
     * @param model the model
     * @param inputTokens number of input tokens
     * @param outputTokens number of output tokens
     * @return total cost
     */
    public static double calculateCost(ModelInfo model, long inputTokens, long outputTokens) {
        if (model.getCost() == null) {
            return 0.0;
        }
        return model.getCost().calculateCost(inputTokens, outputTokens);
    }

    /**
     * Calculate cost with caching.
     *
     * @param model the model
     * @param inputTokens number of input tokens
     * @param outputTokens number of output tokens
     * @param cacheReadTokens number of cache read tokens
     * @param cacheWriteTokens number of cache write tokens
     * @return total cost
     */
    public static double calculateCostWithCache(
            ModelInfo model, long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {
        if (model.getCost() == null) {
            return 0.0;
        }
        return model.getCost().calculateCostWithCache(inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens);
    }

    /**
     * Calculate monthly cost based on daily usage.
     *
     * @param model the model
     * @param dailyInputTokens average daily input tokens
     * @param dailyOutputTokens average daily output tokens
     * @return estimated monthly cost
     */
    public static double calculateMonthlyCost(ModelInfo model, long dailyInputTokens, long dailyOutputTokens) {
        double dailyCost = calculateCost(model, dailyInputTokens, dailyOutputTokens);
        return dailyCost * 30;
    }

    /**
     * Compare cost efficiency between two models for the same workload.
     *
     * @param model1 first model
     * @param model2 second model
     * @param inputTokens number of input tokens
     * @param outputTokens number of output tokens
     * @return negative if model1 is cheaper, positive if model2 is cheaper, 0 if equal
     */
    public static double compareCost(ModelInfo model1, ModelInfo model2, long inputTokens, long outputTokens) {
        double cost1 = calculateCost(model1, inputTokens, outputTokens);
        double cost2 = calculateCost(model2, inputTokens, outputTokens);
        return cost1 - cost2;
    }

    /**
     * Calculate cost per 1000 tokens (useful for comparison).
     *
     * @param cost the cost object
     * @return cost per 1000 tokens (input + output average)
     */
    public static double costPer1000Tokens(Cost cost) {
        if (cost == null) {
            return 0.0;
        }
        double input = cost.getInput() != null ? cost.getInput() : 0.0;
        double output = cost.getOutput() != null ? cost.getOutput() : 0.0;
        return ((input + output) / 2.0) / 1000.0;
    }

    /**
     * Estimate cost for a document processing task.
     *
     * @param model the model
     * @param documentTokens size of document in tokens
     * @param summaryTokens expected summary size in tokens
     * @param documentCount number of documents to process
     * @return total estimated cost
     */
    public static double estimateDocumentProcessingCost(
            ModelInfo model, long documentTokens, long summaryTokens, int documentCount) {
        double costPerDocument = calculateCost(model, documentTokens, summaryTokens);
        return costPerDocument * documentCount;
    }

    /**
     * Calculate savings from using caching.
     *
     * @param model the model
     * @param inputTokens total input tokens
     * @param outputTokens total output tokens
     * @param cacheHitRate percentage of cache hits (0.0 to 1.0)
     * @return cost savings amount
     */
    public static double calculateCacheSavings(
            ModelInfo model, long inputTokens, long outputTokens, double cacheHitRate) {
        if (model.getCost() == null || model.getCost().getCacheRead() == null) {
            return 0.0;
        }

        long cacheReadTokens = (long) (inputTokens * cacheHitRate);
        long actualInputTokens = inputTokens - cacheReadTokens;

        double costWithoutCache = calculateCost(model, inputTokens, outputTokens);
        double costWithCache = calculateCostWithCache(model, actualInputTokens, outputTokens, cacheReadTokens, 0);

        return costWithoutCache - costWithCache;
    }
}
