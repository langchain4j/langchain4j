package com.example.support;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class PolicyExpertTool {

    private final PolicyExpert policyExpert;

    public PolicyExpertTool(PolicyExpert policyExpert) {
        this.policyExpert = policyExpert;
    }

    @Tool("Asks the policy expert a question about cancellations, refunds or pets")
    public String askPolicyExpert(@P(name = "question", description = "The question about a policy") String question) {
        return policyExpert.answer(question);
    }
}
