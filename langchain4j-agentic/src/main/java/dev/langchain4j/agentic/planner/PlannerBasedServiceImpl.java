package dev.langchain4j.agentic.planner;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import java.lang.reflect.Method;
import java.util.function.Supplier;
import dev.langchain4j.agentic.internal.AbstractServiceBuilder;

public class PlannerBasedServiceImpl<T> extends AbstractServiceBuilder<T, PlannerBasedService<T>> implements PlannerBasedService<T> {

    private Supplier<Planner> plannerSupplier;

    public PlannerBasedServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    public static <T> PlannerBasedService<T> builder(Class<T> agentServiceClass) {
        return new PlannerBasedServiceImpl<>(agentServiceClass, validateAgentClass(agentServiceClass, false));
    }

    @Override
    public String serviceType() {
        return "Planner";
    }

    @Override
    public PlannerBasedService<T> planner(Supplier<Planner> plannerSupplier) {
        this.plannerSupplier = plannerSupplier;
        return this;
    }

    @Override
    public T build() {
        return build(plannerSupplier);
    }
}
