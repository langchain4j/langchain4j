package dev.langchain4j.agentic.planner;

import java.util.function.Supplier;

public interface PlannerBasedService<T> extends AgenticService<PlannerBasedService<T>> {

    T build(Supplier<Planner> plannerSupplier);
}
