package dev.langchain4j.service.decision;

import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.service.decision.internal.DecisionMethod;
import dev.langchain4j.service.decision.internal.DecisionServiceConfig;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.spi.ServiceHelper.loadFactory;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.spi.services.DecisionServicesFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates implementations of Java interfaces whose methods are answered by a {@link DecisionModel}.
 * <p>
 * The parameters of a method are sent to the model as the input, keyed by parameter name, and the return type
 * determines the questions:
 * <ul>
 *     <li>{@code boolean}: a yes/no question, {@code true} when the probability of "yes" reaches the threshold
 *     (see {@link Builder#thresholdProvider(ThresholdProvider)})</li>
 *     <li>{@link YesNoAnswer}: a yes/no question, with the probability of "yes"</li>
 *     <li>an enum: a question choosing one of its constants</li>
 *     <li>{@link Choice Choice&lt;enum&gt;}: the same, with the probability of each constant</li>
 *     <li>{@link Scale Scale&lt;enum&gt;}: a question placing the input on an ordered scale, whose levels are the
 *     constants from the first (lowest) to the last (highest)</li>
 *     <li>an object or record whose fields are of the types above: one question per field, in a single call</li>
 * </ul>
 * Any of these can be wrapped in {@link DecisionResult} (to also get the raw response) and/or in
 * {@link java.util.concurrent.CompletableFuture} or {@link java.util.concurrent.CompletionStage} (to call the model
 * asynchronously).
 * <p>
 * A parameter of type {@link dev.langchain4j.model.decision.request.DecisionRequestParameters} is not sent as part of
 * the input: it sets the parameters of the call, such as the model name, overriding the model's defaults.
 * <p>
 * The question is set with {@link Decide @Decide}: on the method, or on the fields of the returned object.
 * The meaning of enum constants is described with
 * {@link dev.langchain4j.model.output.structured.Description @Description}:
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
 *     Triage triage(String ticket);
 *
 *     @Decide("Is this message spam?")
 *     YesNoAnswer isSpam(String message);
 *
 *     @Decide("Which team should handle this ticket?")
 *     Choice<Team> route(String ticket);
 * }
 *
 * SupportDesk supportDesk = DecisionServices.builder(SupportDesk.class)
 *         .decisionModel(decisionModel)
 *         .build();
 * }</pre>
 * The created implementation holds no state of its own, so it can be shared between threads.
 *
 * @since 1.21.0
 */
@Experimental
public final class DecisionServices {

    private DecisionServices() {}

    private static class FactoryHolder {
        private static final DecisionServicesFactory FACTORY = loadFactory(DecisionServicesFactory.class);
    }

    /**
     * Creates an implementation of the given interface whose methods are answered by the given decision model.
     * Equivalent to {@code builder(serviceInterface).decisionModel(decisionModel).build()}.
     */
    public static <T> T create(Class<T> serviceInterface, DecisionModel decisionModel) {
        return builder(serviceInterface).decisionModel(decisionModel).build();
    }

    /**
     * Creates a builder for an implementation of the given interface.
     */
    public static <T> Builder<T> builder(Class<T> serviceInterface) {
        return FactoryHolder.FACTORY != null
                ? FactoryHolder.FACTORY.create(serviceInterface)
                : new Builder<>(serviceInterface);
    }

    /**
     * Builds decision services. Frameworks can extend it (see {@link DecisionServicesFactory}) to build their own
     * implementations, for example at build time, using {@link DecisionMethod} for the analysis of each method.
     */
    public static class Builder<T> {

        private final Class<T> serviceInterface;
        private DecisionModel decisionModel;
        private ThresholdProvider thresholdProvider;

        protected Builder(Class<T> serviceInterface) {
            this.serviceInterface = ensureNotNull(serviceInterface, "serviceInterface");
        }

        public Builder<T> decisionModel(DecisionModel decisionModel) {
            this.decisionModel = decisionModel;
            return this;
        }

        /**
         * Provides the threshold for {@code boolean} answers: the answer is {@code true} when the probability of
         * "yes" is greater than or equal to the threshold. The provider is called on every invocation, so thresholds
         * can come from configuration that changes at runtime. When it returns {@code null}, or is not set, the
         * threshold is 0.5.
         */
        public Builder<T> thresholdProvider(ThresholdProvider thresholdProvider) {
            this.thresholdProvider = thresholdProvider;
            return this;
        }

        protected Class<T> serviceInterface() {
            return serviceInterface;
        }

        protected DecisionModel decisionModel() {
            return decisionModel;
        }

        protected ThresholdProvider thresholdProvider() {
            return thresholdProvider;
        }

        public T build() {
            if (decisionModel == null) {
                throw illegalConfiguration(
                        "The decision model must be set: DecisionServices.builder(%s.class).decisionModel(...)",
                        serviceInterface.getSimpleName());
            }
            if (!serviceInterface.isInterface()) {
                throw illegalConfiguration("%s must be an interface", serviceInterface.getName());
            }

            Map<Method, DecisionMethod> methods = new HashMap<>();
            for (Method method : serviceInterface.getMethods()) {
                if (!method.isDefault() && !Modifier.isStatic(method.getModifiers())) {
                    methods.put(method, DecisionMethod.of(method));
                }
            }
            Map<Method, DecisionMethod> decisionMethods = Map.copyOf(methods);

            DecisionServiceConfig config = DecisionServiceConfig.builder()
                    .serviceInterface(serviceInterface)
                    .decisionModel(decisionModel)
                    .thresholdProvider(thresholdProvider)
                    .build();
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
                return decisionMethods.get(method).invoke(config, args == null ? new Object[0] : args);
            };

            @SuppressWarnings("unchecked")
            T service = (T) Proxy.newProxyInstance(
                    serviceInterface.getClassLoader(), new Class<?>[] {serviceInterface}, handler);
            return service;
        }
    }
}
