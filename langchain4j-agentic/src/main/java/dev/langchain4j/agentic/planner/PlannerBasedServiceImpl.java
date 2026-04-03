package dev.langchain4j.agentic.planner;

import static dev.langchain4j.agentic.declarative.DeclarativeUtil.buildAgentFeatures;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.checkReturnType;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.configureOutput;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.invokeStatic;
import static dev.langchain4j.agentic.internal.AgentUtil.getAnnotatedMethodOnClass;
import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import java.lang.reflect.Method;
import java.util.function.Supplier;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.internal.AbstractServiceBuilder;

public class PlannerBasedServiceImpl<T> extends AbstractServiceBuilder<T, PlannerBasedService<T>> implements PlannerBasedService<T> {

    private Supplier<Planner> plannerSupplier;

    public PlannerBasedServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
        configurePlanner(agentServiceClass);
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

    private void configurePlanner(Class<T> agentServiceClass) {
        configureOutput(agentServiceClass, this);
        buildAgentFeatures(agentServiceClass, this);

        getAnnotatedMethodOnClass(agentServiceClass, PlannerSupplier.class)
                .ifPresentOrElse(
                        method -> {
                            checkReturnType(method, Planner.class);
                            planner(() -> invokeStatic(method));
                        },
                        () -> new IllegalArgumentException(
                                "A planner agent requires a method annotated with @PlannerSupplier that returns the Planner instance."));
    }
}
