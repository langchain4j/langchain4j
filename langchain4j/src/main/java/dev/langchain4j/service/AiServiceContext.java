package dev.langchain4j.service;

import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.spi.ServiceHelper.loadFactory;
import static java.lang.reflect.Modifier.isStatic;

import dev.langchain4j.Internal;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.guardrail.GuardrailService;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.service.memory.ChatMemoryService;
import dev.langchain4j.service.tool.ToolService;
import dev.langchain4j.spi.services.AiServiceContextFactory;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

@Internal
public class AiServiceContext {

    private static final Function<Object, Optional<String>> DEFAULT_MESSAGE_PROVIDER = x -> Optional.empty();

    public final Class<?> aiServiceClass;
    public final AiServiceListenerRegistrar eventListenerRegistrar = AiServiceListenerRegistrar.newInstance();

    public ChatModel chatModel;
    public StreamingChatModel streamingChatModel;

    public ChatMemoryService chatMemoryService;

    public ToolService toolService = new ToolService();

    public final GuardrailService.Builder guardrailServiceBuilder;
    private final AtomicReference<GuardrailService> guardrailService = new AtomicReference<>();

    public ModerationModel moderationModel;

    public RetrievalAugmentor retrievalAugmentor;

    public Function<Object, Optional<String>> systemMessageProvider = DEFAULT_MESSAGE_PROVIDER;

    public BiFunction<ChatRequest, Object, ChatRequest> chatRequestTransformer = (req, memId) -> req;

    private final Set<Method> validMethods = new HashSet<>();

    protected AiServiceContext(Class<?> aiServiceClass) {
        this.aiServiceClass = aiServiceClass;
        this.guardrailServiceBuilder = GuardrailService.builder(aiServiceClass);
    }

    private static class FactoryHolder {
        private static final AiServiceContextFactory contextFactory = loadFactory(AiServiceContextFactory.class);
    }

    public static AiServiceContext create(Class<?> aiServiceClass) {
        return FactoryHolder.contextFactory != null
                ? FactoryHolder.contextFactory.create(aiServiceClass)
                : new AiServiceContext(aiServiceClass);
    }

    public boolean hasChatMemory() {
        return chatMemoryService != null;
    }

    public void initChatMemories(ChatMemory chatMemory) {
        chatMemoryService = new ChatMemoryService(chatMemory);
    }

    public void initChatMemories(ChatMemoryProvider chatMemoryProvider) {
        chatMemoryService = new ChatMemoryService(chatMemoryProvider);
    }

    public GuardrailService guardrailService() {
        return this.guardrailService.updateAndGet(
                service -> (service != null) ? service : guardrailServiceBuilder.build());
    }

    void validate() {
        validateContextMemory();
        validateClass();
        Stream.of(aiServiceClass.getMethods()).forEach(this::validateMethod);
    }

    private void validateContextMemory() {
        if (!hasChatMemory() && ChatMemoryAccess.class.isAssignableFrom(aiServiceClass)) {
            throw illegalConfiguration(
                    "In order to have a service implementing ChatMemoryAccess, please configure the ChatMemoryProvider on the '%s'.",
                    aiServiceClass.getName());
        }
    }

    private void validateClass() {
        if (!aiServiceClass.isInterface()) {
            throw illegalConfiguration(
                    "The type implemented by the AI Service must be an interface, found '%s'",
                    aiServiceClass.getName());
        }
    }

    private void validateMethod(Method method) {
        if (isStatic(method.getModifiers())) {
            // ignore static methods
            return;
        }

        if (method.isAnnotationPresent(Moderate.class) && moderationModel == null) {
            throw illegalConfiguration(
                    "The @Moderate annotation is present, but the moderationModel is not set up. "
                            + "Please ensure a valid moderationModel is configured before using the @Moderate annotation.");
        }

        Class<?> returnType = method.getReturnType();
        if (returnType == Result.class || returnType == List.class || returnType == Set.class) {
            TypeUtils.validateReturnTypesAreProperlyParametrized(method.getName(), method.getGenericReturnType());
        }

        if (!hasChatMemory()) {
            for (Parameter parameter : method.getParameters()) {
                if (parameter.isAnnotationPresent(MemoryId.class)) {
                    throw illegalConfiguration(
                            "In order to use @MemoryId, please configure the ChatMemoryProvider on the '%s'.",
                            aiServiceClass.getName());
                }
            }
        }
    }

    void validateParameters(Method method) {
        if (!validMethods.add(method)) {
            return;
        }

        Parameter[] parameters = method.getParameters();
        if (parameters == null || parameters.length < 2) {
            return;
        }

        boolean invocationParametersExist = false;

        for (Parameter p : parameters) {
            if (InvocationParameters.class.isAssignableFrom(p.getType())) {
                if (invocationParametersExist) {
                    throw illegalConfiguration(
                            "The method '%s' of the class %s has more than one parameter of type %s",
                            method.getName(),
                            aiServiceClass.getName(),
                            InvocationParameters.class.getName());
                }
                invocationParametersExist = true;
                continue;
            }

            if (LangChain4jManaged.class.isAssignableFrom(p.getType())) {
                continue;
            }

            if (!ParameterNameResolver.hasName(p) && p.getAnnotation(UserMessage.class) == null &&
                    p.getAnnotation(MemoryId.class) == null && p.getAnnotation(UserName.class) == null) {
                throw illegalConfiguration(
                        "The parameter '%s' in the method '%s' of the class %s must be annotated with either "
                                + "%s, %s, %s, or %s, or it should be of type %s",
                        p.getName(),
                        method.getName(),
                        aiServiceClass.getName(),
                        dev.langchain4j.service.UserMessage.class.getName(),
                        V.class.getName(),
                        MemoryId.class.getName(),
                        UserName.class.getName(),
                        InvocationParameters.class.getName());
            }
        }
    }
}
