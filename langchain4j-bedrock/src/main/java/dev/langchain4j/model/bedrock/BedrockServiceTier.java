package dev.langchain4j.model.bedrock;

/**
 * Amazon Bedrock offers four service tiers for model inference: Reserved, Priority, Standard, and Flex.
 * With service tiers, you can optimize for availability, cost, and performance.
 *
 * More info <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/service-tiers-inference.html">Service tiers for optimizing performance and cost</a>
 */
public enum BedrockServiceTier {
    /**
     * The Priority tier delivers the fastest response times for a price premium over standard on-demand pricing.
     * It is best suited for mission-critical applications with customer-facing business workflows that do not warrant 24X7 capacity reservation.
     * Priority tier does not require prior reservation.
     * You can simply set the "service_tier" optional parameter to "priority" to avail request level prioritization.
     * Priority tier requests are prioritized over Standard and Flex tier requests.
     */
    PRIORITY,
    /**
     * The Standard tier provides consistent performance for everyday AI tasks such as content generation, text analysis, and routine document processing.
     * By default all inference requests are routed to the Standard tier when the "service_tier" parameter is missing.
     * You can also set the "service_tier" optional parameter to "default" for your inference request to be served with Standard tier.
     */
    DEFAULT,
    /**
     * For workloads that can handle longer processing times, the Flex tier offers cost-effective processing for a pricing discount.
     * This helps you optimize cost for workloads such as model evaluations, content summarization, and agentic workflows.
     * You can set the "service_tier" optional parameter to "flex" for your inference request to be served with the Flex tier and avail the pricing discount.
     */
    FLEX,
    /**
     * The Reserved tier provides the ability to reserve prioritized compute capacity for your mission-critical applications that cannot tolerate any downtime.
     * You have the flexibility to allocate different input and output tokens-per-minute capacities to match the exact requirements of your workload and control cost.
     * When your application needs more tokens-per-minute capacity than what you reserved, the service automatically overflows to the Standard tier, ensuring uninterrupted operations.
     * The Reserved tier targets 99.5% uptime for model response.
     * Customers can reserve capacity for 1 month or 3 month duration.
     * Customers pay a fixed price per 1K tokens-per-minute and are billed monthly.
     **/
    RESERVED
}
