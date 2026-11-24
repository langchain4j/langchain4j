package com.example.support;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class CustomerContext {

    private String customerId;

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    @Tool("Returns the loyalty tier of the current customer")
    public String getCustomerTier() {
        if (customerId == null) {
            throw new IllegalStateException("No customer is logged in");
        }
        return customerId.startsWith("VIP-") ? "gold" : "standard";
    }
}
