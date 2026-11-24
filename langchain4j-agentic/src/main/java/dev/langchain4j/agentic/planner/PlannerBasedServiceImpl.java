package dev.langchain4j.agentic.planner;

import static dev.langchain4j.agentic.declarative.DeclarativeUtil.buildAgentFeatures;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.checkReturnType;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.configureOutput;
import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.internal.AbstractServiceBuilder;
import dev.langchain4j.agentic.internal.AgentHelpers;
import dev.langchain4j.agentic.internal.ResolvedParameters;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

public class PlannerBasedServiceImpl<T> extends AbstractServiceBuilder<T, PlannerBasedService<T>>
        implements PlannerBasedService<T> {

    private Supplier<Planner> plannerSupplier;

    public PlannerBasedServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        this(agentServiceClass, agenticMethod, List.of());
    }

    /**
     * @param helperObjects the helper objects of the agent, whose instance helper methods configure it together
     *        with the static methods of its type
     */
    public PlannerBasedServiceImpl(Class<T> agentServiceClass, Method agenticMethod, List<Object> helperObjects) {
        super(agentServiceClass, agenticMethod);
        configurePlanner(AgentHelpers.of(agentServiceClass, helperObjects));
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
        if (plannerSupplier == null) {
            throw new IllegalArgumentException(
                    "A planner agent requires a Planner, provided either via planner(...) or, for declarative agents, "
                            + "a method annotated with @PlannerSupplier that returns the Planner instance.");
        }
        return build(plannerSupplier);
    }

    private void configurePlanner(AgentHelpers helpers) {
        configureOutput(helpers, this);
        buildAgentFeatures(helpers, this);

        helpers.select(PlannerSupplier.class).ifPresent(helper -> {
            checkReturnType(helper.method(), Planner.class);
            ResolvedParameters parameters =
                    ResolvedParameters.of(helpers.agentType(), helper).checkAllResolved();
            planner(() -> parameters.invoke());
        });
    }
}
