package dev.langchain4j.service.decision;

import dev.langchain4j.spi.services.DecisionServicesFactory;

public class TestDecisionServicesFactory implements DecisionServicesFactory {

    @Override
    public <T> DecisionServices.Builder<T> create(Class<T> serviceInterface) {
        return new TestBuilder<>(serviceInterface);
    }

    static class TestBuilder<T> extends DecisionServices.Builder<T> {

        TestBuilder(Class<T> serviceInterface) {
            super(serviceInterface);
        }
    }
}
