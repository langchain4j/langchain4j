package dev.langchain4j.service.decision;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.decision.DecisionModel;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Creates implementations of Java interfaces whose methods are answered by a {@link DecisionModel}.
 * <p>
 * The parameters of a method are sent to the model as the state, keyed by parameter name, and the return type
 * determines the questions:
 * <ul>
 *     <li>{@code boolean}: a yes/no question, {@code true} when the probability of "yes" reaches the threshold</li>
 *     <li>{@link YesNo}: a yes/no question, with the probability of "yes"</li>
 *     <li>an enum: a question choosing one of its constants</li>
 *     <li>{@link Choice Choice&lt;enum&gt;}: the same, with the probability of each constant</li>
 *     <li>an object or record whose fields are of the types above: one question per field, in a single call</li>
 * </ul>
 * Any of these can be wrapped in {@link DecisionResult} (to also get the raw response) and/or in
 * {@link java.util.concurrent.CompletableFuture} (to call the model asynchronously).
 * <p>
 * The question is set with {@link Decide @Decide}: on the method, or on the fields of the returned object (where
 * {@link dev.langchain4j.model.output.structured.Description @Description} is also accepted).
 * The meaning of enum constants is described with {@code @Description}:
 * <pre>{@code
 * enum Team {
 *     @Description("Payments, invoices, refunds") BILLING,
 *     @Description("Problems using the product") SUPPORT
 * }
 *
 * class Triage {
 *     @Decide("Which team should handle this ticket?") Team team;
 *     @Decide("Does this need attention today?") boolean urgent;
 * }
 *
 * interface SupportDesk {
 *
 *     Triage triage(String ticket, String plan);
 *
 *     @Decide("Is this message spam?")
 *     boolean isSpam(String message, @Threshold double threshold);
 *
 *     @Decide("Which team should handle this ticket?")
 *     Choice<Team> route(String ticket);
 * }
 *
 * SupportDesk supportDesk = DecisionServices.builder(SupportDesk.class)
 *         .decisionModel(decisionModel)
 *         .build();
 * }</pre>
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionServices {

    private DecisionServices() {}

    public static <T> Builder<T> builder(Class<T> serviceInterface) {
        return new Builder<>(serviceInterface);
    }

    public static class Builder<T> {

        private final Class<T> serviceInterface;
        private DecisionModel decisionModel;
        private Function<String, Double> thresholdProvider;

        private Builder(Class<T> serviceInterface) {
            this.serviceInterface = ensureNotNull(serviceInterface, "serviceInterface");
        }

        public Builder<T> decisionModel(DecisionModel decisionModel) {
            this.decisionModel = decisionModel;
            return this;
        }

        /**
         * Provides the threshold for {@code boolean} answers that have no {@link Threshold @Threshold} parameter.
         * The function receives the name of the question (the method name, or the field name for methods returning
         * an object) and is called on every invocation, so the thresholds can come from configuration that changes
         * at runtime. When it returns {@code null}, or is not set, the threshold is 0.5.
         */
        public Builder<T> thresholdProvider(Function<String, Double> thresholdProvider) {
            this.thresholdProvider = thresholdProvider;
            return this;
        }

        public T build() {
            ensureNotNull(decisionModel, "decisionModel");
            if (!serviceInterface.isInterface()) {
                throw illegalConfiguration("%s must be an interface", serviceInterface.getName());
            }

            Map<Method, DecisionMethod> methods = new HashMap<>();
            for (Method method : serviceInterface.getMethods()) {
                if (!method.isDefault() && !Modifier.isStatic(method.getModifiers())) {
                    methods.put(method, new DecisionMethod(method));
                }
            }

            DecisionModel model = decisionModel;
            Function<String, Double> thresholds = thresholdProvider;
            InvocationHandler handler = (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "DecisionService(" + serviceInterface.getName() + ")";
                        default -> throw new IllegalStateException("Unexpected method: " + method);
                    };
                }
                if (method.isDefault()) {
                    return InvocationHandler.invokeDefault(proxy, method, args);
                }
                return methods.get(method).invoke(model, args == null ? new Object[0] : args, thresholds);
            };

            @SuppressWarnings("unchecked")
            T service = (T) Proxy.newProxyInstance(
                    serviceInterface.getClassLoader(), new Class<?>[] {serviceInterface}, handler);
            return service;
        }
    }
}
