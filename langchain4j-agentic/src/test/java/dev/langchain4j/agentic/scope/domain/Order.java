package dev.langchain4j.agentic.scope.domain;

/**
 * A domain type in a package of its own, so that a test may register the whole package without
 * widening the allowlist for anything else in the JVM - registrations are process-wide.
 */
public class Order {

    private String sku;

    public Order() {}

    public Order(String sku) {
        this.sku = sku;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }
}
