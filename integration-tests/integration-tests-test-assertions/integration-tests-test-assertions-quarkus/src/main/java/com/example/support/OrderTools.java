package com.example.support;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class OrderTools {

    private static final Map<String, String> STATUSES = Map.of(
            "ORD-1", "shipped",
            "ORD-2", "processing");

    private final List<String> cancelledOrders = new ArrayList<>();

    @Tool("Returns the status of the order with the given number")
    public String getOrderStatus(String orderNumber) {
        String status = STATUSES.get(orderNumber);
        if (status == null) {
            throw new IllegalArgumentException("Order " + orderNumber + " not found");
        }
        return status;
    }

    @Tool("Cancels the order with the given number")
    public String cancelOrder(String orderNumber) {
        cancelledOrders.add(orderNumber);
        return "Order " + orderNumber + " cancelled";
    }

    public List<String> cancelledOrders() {
        return cancelledOrders;
    }
}
