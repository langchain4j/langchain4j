package dev.langchain4j.service.guardrail;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.classinstance.ClassInstanceLoader;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the {@link ToolInputGuardrail}s and {@link ToolOutputGuardrail}s configured for each tool.
 * <p>
 * Guardrails are chained: what one rewrites is what the next one sees, and what the last one leaves is
 * what actually executes.
 *
 * @since 1.19.0
 */
@Internal
public class ToolGuardrailService {

    private static final Logger log = LoggerFactory.getLogger(ToolGuardrailService.class);

    private final List<ToolInputGuardrail> globalInputGuardrails = new ArrayList<>();
    private final List<ToolOutputGuardrail> globalOutputGuardrails = new ArrayList<>();
    private final Map<String, List<ToolInputGuardrail>> inputGuardrailsByTool = new LinkedHashMap<>();
    private final Map<String, List<ToolOutputGuardrail>> outputGuardrailsByTool = new LinkedHashMap<>();

    /**
     * Adds guardrails that apply to every tool of the AI Service, including tools that have no declaring
     * class to annotate, such as MCP tools.
     */
    public void addGlobalInputGuardrails(Collection<? extends ToolInputGuardrail> guardrails) {
        if (guardrails != null) {
            globalInputGuardrails.addAll(guardrails);
        }
    }

    public void addGlobalOutputGuardrails(Collection<? extends ToolOutputGuardrail> guardrails) {
        if (guardrails != null) {
            globalOutputGuardrails.addAll(guardrails);
        }
    }

    public void addGlobalInputGuardrailClasses(Collection<Class<? extends ToolInputGuardrail>> guardrailClasses) {
        if (guardrailClasses != null) {
            guardrailClasses.forEach(
                    guardrailClass -> globalInputGuardrails.add(ClassInstanceLoader.getClassInstance(guardrailClass)));
        }
    }

    public void addGlobalOutputGuardrailClasses(Collection<Class<? extends ToolOutputGuardrail>> guardrailClasses) {
        if (guardrailClasses != null) {
            guardrailClasses.forEach(
                    guardrailClass -> globalOutputGuardrails.add(ClassInstanceLoader.getClassInstance(guardrailClass)));
        }
    }

    /**
     * Registers the guardrails declared by annotations on the given tool method and its declaring class.
     */
    public void registerAnnotatedGuardrails(String toolName, Method toolMethod) {
        List<ToolInputGuardrail> inputGuardrails = new ArrayList<>();
        addAnnotated(
                inputGuardrails,
                toolMethod.getDeclaringClass().getAnnotation(ToolInputGuardrails.class),
                toolMethod.getAnnotation(ToolInputGuardrails.class));
        if (!inputGuardrails.isEmpty()) {
            inputGuardrailsByTool.put(toolName, inputGuardrails);
        }

        List<ToolOutputGuardrail> outputGuardrails = new ArrayList<>();
        addAnnotated(
                outputGuardrails,
                toolMethod.getDeclaringClass().getAnnotation(ToolOutputGuardrails.class),
                toolMethod.getAnnotation(ToolOutputGuardrails.class));
        if (!outputGuardrails.isEmpty()) {
            outputGuardrailsByTool.put(toolName, outputGuardrails);
        }
    }

    public boolean hasInputGuardrails(String toolName) {
        return !globalInputGuardrails.isEmpty() || inputGuardrailsByTool.containsKey(toolName);
    }

    public boolean hasOutputGuardrails(String toolName) {
        return !globalOutputGuardrails.isEmpty() || outputGuardrailsByTool.containsKey(toolName);
    }

    public boolean isEmpty() {
        return globalInputGuardrails.isEmpty()
                && globalOutputGuardrails.isEmpty()
                && inputGuardrailsByTool.isEmpty()
                && outputGuardrailsByTool.isEmpty();
    }

    /**
     * Runs every input guardrail for this tool.
     *
     * @return the tool call to execute, possibly rewritten
     * @throws ToolGuardrailException if a guardrail refused the call
     */
    public ToolExecutionRequest executeInputGuardrails(
            ToolExecutionRequest executionRequest, ToolMetadata toolMetadata, InvocationContext invocationContext) {

        ToolInputGuardrailRequest request =
                new ToolInputGuardrailRequest(executionRequest, toolMetadata, invocationContext);

        for (ToolInputGuardrail guardrail : guardrailsFor(globalInputGuardrails, inputGuardrailsByTool, request)) {
            ToolInputGuardrailResult result = validate(guardrail, request);
            throwIfFailed(result, guardrail, executionRequest.name(), "Input");
            if (result.hasRewrittenResult()) {
                request = request.with(result.executionRequest(request));
            }
        }
        return request.executionRequest();
    }

    /**
     * Runs every output guardrail for this tool.
     *
     * @return the result to hand back to the LLM, possibly rewritten
     * @throws ToolGuardrailException if a guardrail rejected the result
     */
    public ToolExecutionResult executeOutputGuardrails(
            ToolExecutionResult executionResult,
            ToolExecutionRequest executionRequest,
            ToolMetadata toolMetadata,
            InvocationContext invocationContext) {

        ToolOutputGuardrailRequest request =
                new ToolOutputGuardrailRequest(executionResult, executionRequest, toolMetadata, invocationContext);

        for (ToolOutputGuardrail guardrail : guardrailsFor(globalOutputGuardrails, outputGuardrailsByTool, request)) {
            ToolOutputGuardrailResult result = validate(guardrail, request);
            throwIfFailed(result, guardrail, executionRequest.name(), "Output");
            if (result.hasRewrittenResult()) {
                request = request.with(result.executionResult(request));
            }
        }
        return request.executionResult();
    }

    private static <G> List<G> guardrailsFor(
            List<G> global, Map<String, List<G>> byTool, ToolGuardrailRequest<?> request) {
        List<G> perTool = byTool.get(request.toolName());
        if (perTool == null) {
            return global;
        }
        if (global.isEmpty()) {
            return perTool;
        }
        List<G> all = new ArrayList<>(global);
        all.addAll(perTool);
        return all;
    }

    private static <P extends ToolGuardrailRequest<P>, R extends ToolGuardrailResult<R>> R validate(
            ToolGuardrail<P, R> guardrail, P request) {
        try {
            return guardrail.validate(request).validatedBy(guardrail.getClass());
        } catch (ToolGuardrailException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolGuardrailException(
                    "Tool guardrail %s threw an exception while validating tool '%s'"
                            .formatted(guardrail.name(), request.toolName()),
                    e);
        }
    }

    private static void throwIfFailed(
            ToolGuardrailResult<?> result, ToolGuardrail<?, ?> guardrail, String toolName, String direction) {
        if (result.isSuccess()) {
            return;
        }
        String message = "%s validation of tool '%s' failed: %s".formatted(direction, toolName, result.asString());
        if (result.isFatal()) {
            log.error(message);
            throw new ToolGuardrailException(message, result.getFirstFailureException(), true);
        }
        log.warn("{} (the LLM will be told)", message);
        throw new ToolGuardrailException(message, result.getFirstFailureException(), false);
    }

    private static <G> void addAnnotated(List<G> target, Object classAnnotation, Object methodAnnotation) {
        addAnnotated(target, classAnnotation);
        addAnnotated(target, methodAnnotation);
    }

    @SuppressWarnings("unchecked")
    private static <G> void addAnnotated(List<G> target, Object annotation) {
        if (annotation instanceof ToolInputGuardrails inputGuardrails) {
            for (Class<? extends ToolInputGuardrail> guardrailClass : inputGuardrails.value()) {
                target.add((G) ClassInstanceLoader.getClassInstance(guardrailClass));
            }
        } else if (annotation instanceof ToolOutputGuardrails outputGuardrails) {
            for (Class<? extends ToolOutputGuardrail> guardrailClass : outputGuardrails.value()) {
                target.add((G) ClassInstanceLoader.getClassInstance(guardrailClass));
            }
        }
    }
}
