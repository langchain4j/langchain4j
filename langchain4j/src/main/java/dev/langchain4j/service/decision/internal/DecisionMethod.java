package dev.langchain4j.service.decision.internal;

import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.service.decision.ThresholdProvider;
import dev.langchain4j.service.decision.ThresholdContext;
import dev.langchain4j.service.decision.DecisionServices;
import dev.langchain4j.service.decision.DecisionResult;
import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.decision.Choice;
import dev.langchain4j.service.decision.Scale;
import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;

import dev.langchain4j.Internal;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.ParameterNameResolver;
import dev.langchain4j.internal.Json;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.DoubleSupplier;
import java.util.function.Function;

/**
 * The analysis of one decision service method: the questions it asks, how its parameters become the input, and how
 * the answers are mapped back to its return type.
 * <p>
 * All validation happens in {@link #of(Method)}, so frameworks can detect errors at build time. At runtime, they
 * can call {@link #invoke} or {@link #invokeAsync} to implement the method without the proxy created by
 * {@link DecisionServices}.
 */
@Internal
public final class DecisionMethod {

    private static final double DEFAULT_THRESHOLD = 0.5;

    private final Method method;
    private final boolean async;
    private final boolean withResponse;
    private final Type contentType;
    private final List<InputParameter> inputParameters;
    private final int requestParametersIndex;
    private final Map<String, QuestionMapping> mappings;
    private final Map<String, Question> questions;
    private final Set<Class<?>> reflectiveTypes;
    private final List<Type> inputTypes;
    private final Function<Map<String, Object>, Object> resultFactory;

    private DecisionMethod(Method method, Type returnType) {
        this.method = method;
        Analysis analysis = new Analysis(method);

        Type type = returnType;
        this.async = isParameterizedBy(type, CompletableFuture.class) || isParameterizedBy(type, CompletionStage.class);
        if (async) {
            type = typeArgument(type);
        }
        this.withResponse = isParameterizedBy(type, DecisionResult.class);
        if (withResponse) {
            type = typeArgument(type);
        }
        this.contentType = type;

        Decide decide = method.getAnnotation(Decide.class);
        if (kindOf(type) != null) {
            if (decide == null) {
                throw illegalConfiguration(
                        "Method '%s' must be annotated with @Decide, which contains the question to answer",
                        method.getName());
            }
            QuestionMapping single = analysis.mappingFor(method.getName(), type, decide.value());
            analysis.add(single);
            this.resultFactory = values -> values.get(single.name());
        } else if (type instanceof Class<?> objectType && isObjectType(objectType)) {
            if (decide != null) {
                throw illegalConfiguration(
                        "Method '%s' returns %s, which contains several questions. "
                                + "@Decide is not supported on such methods: annotate the fields of %s instead",
                        method.getName(), objectType.getSimpleName(), objectType.getSimpleName());
            }
            analysis.reflectiveTypes.add(objectType);
            this.resultFactory = analysis.objectFactory(objectType);
        } else {
            throw illegalConfiguration(
                    "Method '%s' has an unsupported return type: %s. Supported types are boolean, YesNoAnswer, an enum, "
                            + "Choice<enum>, Scale<enum>, and objects whose fields are of these types, optionally wrapped in "
                            + "DecisionResult<> and/or CompletableFuture<> (or CompletionStage<>)",
                    method.getName(), returnType.getTypeName());
        }

        analysis.analyzeParameters();
        this.inputParameters = List.copyOf(analysis.inputParameters);
        this.requestParametersIndex = analysis.requestParametersIndex;
        this.mappings = Collections.unmodifiableMap(analysis.mappings);
        this.questions = Collections.unmodifiableMap(analysis.questions);
        this.reflectiveTypes = Collections.unmodifiableSet(analysis.reflectiveTypes);
        this.inputTypes = analysis.inputParameters.stream()
                .map(parameter -> method.getGenericParameterTypes()[parameter.index()])
                .toList();
    }

    /**
     * Analyzes the given method of a decision service interface.
     *
     * @throws dev.langchain4j.service.IllegalConfigurationException if the method cannot be implemented.
     */
    public static DecisionMethod of(Method method) {
        ensureNotNull(method, "method");
        return new DecisionMethod(method, method.getGenericReturnType());
    }

    /**
     * Analyzes the given method as if it returned the given type. Frameworks can use this for return types that are
     * not supported here, for example a reactive type such as Mutiny's {@code Uni<T>}: they pass {@code T} and call
     * {@link #invokeAsync(DecisionServiceConfig, Object[])}.
     *
     * @throws dev.langchain4j.service.IllegalConfigurationException if the method cannot be implemented.
     */
    public static DecisionMethod of(Method method, Type returnType) {
        return new DecisionMethod(ensureNotNull(method, "method"), ensureNotNull(returnType, "returnType"));
    }

    public Method method() {
        return method;
    }

    /**
     * The questions asked by this method, keyed by question name.
     */
    public Map<String, Question> questions() {
        return questions;
    }

    /**
     * Whether the method returns a {@link CompletableFuture} or a {@link CompletionStage}.
     */
    public boolean isAsync() {
        return async;
    }

    /**
     * The type of the result, without the {@link CompletableFuture} and {@link DecisionResult} wrappers.
     */
    public Type contentType() {
        return contentType;
    }

    /**
     * The classes accessed by reflection when mapping the answers, so that frameworks can register them for native
     * images: the returned object types and their superclasses (their declared fields and constructors are used, and
     * for records their components), and the enums whose constants are the options (their constant fields are read
     * for {@code @Description}). When {@link #of(Method)} itself runs in a native image, the service interface (its
     * methods, with their parameters and annotations) must be registered as well.
     */
    public Set<Class<?>> reflectiveTypes() {
        return reflectiveTypes;
    }

    /**
     * The types of the parameters that are sent to the model. Objects among them are converted to maps with the JSON
     * codec, so frameworks may need to register them, and the types they contain, for native images.
     */
    public List<Type> inputTypes() {
        return inputTypes;
    }

    /**
     * Creates the request for an invocation of this method with the given arguments.
     */
    public DecisionRequest toRequest(Object[] args) {
        Map<String, Object> input = new LinkedHashMap<>();
        for (InputParameter parameter : inputParameters) {
            Object value = args[parameter.index()];
            if (value != null) {
                input.put(parameter.name(), toInputValue(value));
            }
        }
        if (input.isEmpty()) {
            throw new IllegalArgumentException("All arguments of method '%s' that are sent to the model (%s) are null"
                    .formatted(
                            method.getName(),
                            inputParameters.stream().map(InputParameter::name).toList()));
        }
        return DecisionRequest.builder()
                .input(input)
                .questions(questions)
                .parameters(requestParameters(args))
                .build();
    }

    /**
     * Converts an argument into the structured content accepted by {@link DecisionRequest}: enums become their names
     * and other objects become maps, using the JSON codec (by default, with their Java field names).
     */
    private static Object toInputValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Enum<?> constant) {
            return constant.name();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), toInputValue(item)));
            return result;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>();
            collection.forEach(item -> result.add(toInputValue(item)));
            return result;
        }
        return toInputValue(Json.fromJson(Json.toJson(value), Object.class));
    }

    /**
     * Maps the response to the result of this method, without the {@link CompletableFuture} wrapper.
     *
     * @param thresholds the threshold of each {@code boolean} answer, by question name; {@code null} means 0.5.
     */
    public Object toResult(DecisionResponse response, Function<String, Double> thresholds) {
        Map<String, Object> values = new LinkedHashMap<>();
        mappings.forEach(
                (name, mapping) -> values.put(name, mapping.value(response, () -> threshold(name, thresholds))));
        Object content = resultFactory.apply(values);
        return withResponse
                ? DecisionResult.builder().content(content).response(response).build()
                : content;
    }

    /**
     * Invokes this method with the given configuration of the decision service.
     */
    public Object invoke(DecisionServiceConfig config, Object[] args) {
        if (async) {
            return invokeAsync(config, args);
        }
        DecisionRequest request = toRequest(args);
        DecisionResponse response = config.decisionModel().decide(request);
        return toResult(response, thresholds(config, request, response));
    }

    /**
     * Invokes this method asynchronously with the given model, whatever its return type. The call to the model starts
     * immediately; frameworks with lazy types (such as Mutiny's {@code Uni}) should call this method on subscription.
     *
     * @return a future of the result, without the {@link CompletableFuture} wrapper of the method's return type.
     */
    public CompletableFuture<Object> invokeAsync(DecisionServiceConfig config, Object[] args) {
        DecisionRequest request;
        CompletableFuture<DecisionResponse> source;
        try {
            request = toRequest(args);
            source = ensureNotNull(config.decisionModel().decideAsync(request), "decideAsync result");
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        CompletableFuture<Object> result =
                source.thenApply(response -> toResult(response, thresholds(config, request, response)));
        propagateCancellation(result, source);
        return result;
    }

    private Function<String, Double> thresholds(
            DecisionServiceConfig config, DecisionRequest request, DecisionResponse response) {
        ThresholdProvider thresholdProvider = config.thresholdProvider();
        if (thresholdProvider == null) {
            return questionName -> null;
        }
        String modelName = getOrDefault(
                response.modelName(), () -> getOrDefault(request.modelName(), config.decisionModel().modelName()));
        return questionName -> thresholdProvider.threshold(ThresholdContext.builder()
                .serviceInterface(config.serviceInterface())
                .method(method)
                .questionName(questionName)
                .modelName(modelName)
                .build());
    }

    private double threshold(String questionName, Function<String, Double> thresholds) {
        Double threshold = thresholds == null ? null : thresholds.apply(questionName);
        if (threshold != null && !(threshold >= 0 && threshold <= 1)) {
            throw new IllegalArgumentException(
                    "The threshold for question '%s' of method '%s' must be between 0 and 1, but was %s"
                            .formatted(questionName, method.getName(), threshold));
        }
        return getOrDefault(threshold, DEFAULT_THRESHOLD);
    }

    private DecisionRequestParameters requestParameters(Object[] args) {
        if (requestParametersIndex < 0) {
            return null;
        }
        return (DecisionRequestParameters) args[requestParametersIndex];
    }

    private static Kind kindOf(Type type) {
        if (type == boolean.class || type == Boolean.class) {
            return Kind.BOOLEAN;
        } else if (type == YesNoAnswer.class) {
            return Kind.YES_NO;
        } else if (type instanceof Class<?> c && c.isEnum()) {
            return Kind.ENUM;
        } else if (isParameterizedBy(type, Choice.class)) {
            return Kind.CHOICE;
        } else if (isParameterizedBy(type, Scale.class)) {
            return Kind.SCALE;
        }
        return null;
    }

    private static boolean isObjectType(Class<?> type) {
        return !type.isPrimitive()
                && !type.isArray()
                && !type.isInterface()
                && !type.isEnum()
                && !Modifier.isAbstract(type.getModifiers())
                && !type.getName().startsWith("java.")
                && !Collection.class.isAssignableFrom(type)
                && !Map.class.isAssignableFrom(type);
    }

    private static boolean isParameterizedBy(Type type, Class<?> rawType) {
        return type instanceof ParameterizedType parameterized && parameterized.getRawType() == rawType;
    }

    private static Type typeArgument(Type type) {
        return ((ParameterizedType) type).getActualTypeArguments()[0];
    }

    private static Field declaredField(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object newInstance(Constructor<?> constructor, Object... arguments) {
        try {
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Cannot create an instance of " + constructor.getDeclaringClass().getName(), e);
        }
    }

    /**
     * Collects the results of the analysis before they are stored in the final fields of {@link DecisionMethod}.
     */
    private static final class Analysis {

        private final Method method;
        private final List<InputParameter> inputParameters = new ArrayList<>();
        private int requestParametersIndex = -1;
        private final Map<String, QuestionMapping> mappings = new LinkedHashMap<>();
        private final Map<String, Question> questions = new LinkedHashMap<>();
        private final Set<Class<?>> reflectiveTypes = new LinkedHashSet<>();

        private Analysis(Method method) {
            this.method = method;
        }

        private void analyzeParameters() {
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                if (DecisionRequestParameters.class.isAssignableFrom(parameter.getType())) {
                    if (requestParametersIndex >= 0) {
                        throw illegalConfiguration(
                                "Method '%s' has several DecisionRequestParameters parameters", method.getName());
                    }
                    requestParametersIndex = i;
                    continue;
                }
                if (!ParameterNameResolver.hasName(parameter) && !parameter.isNamePresent()) {
                    throw illegalConfiguration(
                            "The name of parameter %s of method '%s' is not available, but it is sent to the model "
                                    + "together with the value. Compile with the '-parameters' option "
                                    + "or annotate the parameter with @V(\"name\")",
                            i, method.getName());
                }
                String name = ParameterNameResolver.name(parameter);
                if (inputParameters.stream().anyMatch(p -> p.name().equals(name))) {
                    throw illegalConfiguration(
                            "Method '%s' has several parameters named '%s'", method.getName(), name);
                }
                inputParameters.add(new InputParameter(name, i));
            }
            if (inputParameters.isEmpty()) {
                throw illegalConfiguration(
                        "Method '%s' must have at least one parameter that is not DecisionRequestParameters: "
                                + "the parameters are what the model evaluates",
                        method.getName());
            }
        }

        private Function<Map<String, Object>, Object> objectFactory(Class<?> type) {
            if (type.isRecord()) {
                RecordComponent[] components = type.getRecordComponents();
                Class<?>[] componentTypes = new Class<?>[components.length];
                for (int i = 0; i < components.length; i++) {
                    RecordComponent component = components[i];
                    componentTypes[i] = component.getType();
                    add(fieldMapping(declaredField(type, component.getName()), component.getGenericType()));
                }
                Constructor<?> constructor = constructor(type, componentTypes);
                return values -> {
                    Object[] arguments = new Object[components.length];
                    for (int i = 0; i < components.length; i++) {
                        arguments[i] = values.get(components[i].getName());
                    }
                    return newInstance(constructor, arguments);
                };
            }

            if ((type.isMemberClass() && !Modifier.isStatic(type.getModifiers()))
                    || type.isLocalClass()
                    || type.isAnonymousClass()) {
                throw illegalConfiguration(
                        "%s, returned by method '%s', is an inner class: declare it as a static nested class,"
                                + " a top-level class or a record",
                        type.getSimpleName(), method.getName());
            }
            List<Field> fields = new ArrayList<>();
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                reflectiveTypes.add(c);
                for (Field field : c.getDeclaredFields()) {
                    int modifiers = field.getModifiers();
                    if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers) && !field.isSynthetic()) {
                        if (Modifier.isFinal(modifiers)) {
                            throw illegalConfiguration(
                                    "Field '%s' of %s, returned by method '%s', is final: make it non-final,"
                                            + " or return a record",
                                    field.getName(), type.getSimpleName(), method.getName());
                        }
                        fields.add(field);
                    }
                }
            }
            if (fields.isEmpty()) {
                throw illegalConfiguration(
                        "%s, returned by method '%s', has no fields to decide on",
                        type.getSimpleName(), method.getName());
            }
            for (Field field : fields) {
                add(fieldMapping(field, field.getGenericType()));
                field.setAccessible(true);
            }
            Constructor<?> constructor = constructor(type);
            return values -> {
                Object instance = newInstance(constructor);
                for (Field field : fields) {
                    try {
                        field.set(instance, values.get(field.getName()));
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException("Cannot set field '%s'".formatted(field.getName()), e);
                    }
                }
                return instance;
            };
        }

        private QuestionMapping fieldMapping(Field field, Type type) {
            Decide decide = field.getAnnotation(Decide.class);
            if (decide == null) {
                throw illegalConfiguration(
                        "Field '%s' of %s, returned by method '%s', must be annotated with @Decide, "
                                + "which contains the question to answer",
                        field.getName(), field.getDeclaringClass().getSimpleName(), method.getName());
            }
            if (kindOf(type) == null) {
                throw illegalConfiguration(
                        "Field '%s' of %s, returned by method '%s', has an unsupported type: %s. "
                                + "Supported types are boolean, YesNoAnswer, an enum, Choice<enum> and Scale<enum>",
                        field.getName(),
                        field.getDeclaringClass().getSimpleName(),
                        method.getName(),
                        type.getTypeName());
            }
            return mappingFor(field.getName(), type, decide.value());
        }

        private void add(QuestionMapping mapping) {
            if (mappings.containsKey(mapping.name())) {
                throw illegalConfiguration(
                        "The object returned by method '%s' has several fields named '%s' (for example, one of them "
                                + "in a superclass), but each question needs a unique name",
                        method.getName(), mapping.name());
            }
            mappings.put(mapping.name(), mapping);
            questions.put(mapping.name(), mapping.question());
        }

        private QuestionMapping mappingFor(String name, Type type, String questionText) {
            if (isNullOrBlank(questionText)) {
                throw illegalConfiguration(
                        "The question of '%s' of method '%s' in @Decide must not be blank", name, method.getName());
            }
            Kind kind = kindOf(type);
            Class<?> enumType = null;
            if (kind == Kind.ENUM) {
                enumType = (Class<?>) type;
            } else if (kind == Kind.CHOICE || kind == Kind.SCALE) {
                if (!(typeArgument(type) instanceof Class<?> c) || !c.isEnum()) {
                    throw illegalConfiguration(
                            "'%s' of method '%s': %s must be parameterized with an enum",
                            name, method.getName(), kind == Kind.CHOICE ? "Choice" : "Scale");
                }
                enumType = c;
            }
            if (enumType != null) {
                reflectiveTypes.add(enumType);
            }
            Question question;
            if (enumType == null) {
                question = YesNoQuestion.of(questionText);
            } else if (kind == Kind.SCALE) {
                List<String> levels = new ArrayList<>();
                descriptions(name, enumType).forEach((constantName, description) ->
                        levels.add(description == null ? constantName : constantName + ": " + description));
                question = ScaleQuestion.of(questionText, levels);
            } else {
                ChoiceQuestion.Builder choice = ChoiceQuestion.builder().text(questionText);
                descriptions(name, enumType).forEach((constantName, description) -> {
                    if (description == null) {
                        choice.option(constantName);
                    } else {
                        choice.option(constantName, description);
                    }
                });
                question = choice.build();
            }
            return new QuestionMapping(name, question, kind, enumType);
        }

        /**
         * The {@code @Description} of each constant, or {@code null} for a constant without one.
         */
        private Map<String, String> descriptions(String name, Class<?> enumType) {
            Object[] constants = enumType.getEnumConstants();
            if (constants.length < 2) {
                throw illegalConfiguration(
                        "'%s' of method '%s': enum %s must have at least 2 constants",
                        name, method.getName(), enumType.getSimpleName());
            }
            Map<String, String> descriptions = new LinkedHashMap<>();
            for (Object constant : constants) {
                String constantName = ((Enum<?>) constant).name();
                Description description = declaredField(enumType, constantName).getAnnotation(Description.class);
                descriptions.put(constantName, description == null ? null : String.join(" ", description.value()));
            }
            return descriptions;
        }

        private Constructor<?> constructor(Class<?> type, Class<?>... parameterTypes) {
            try {
                Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
                constructor.setAccessible(true);
                return constructor;
            } catch (NoSuchMethodException e) {
                throw illegalConfiguration(
                        "%s, returned by method '%s', must have a no-argument constructor",
                        type.getSimpleName(), method.getName());
            }
        }
    }

    private enum Kind {
        BOOLEAN,
        YES_NO,
        ENUM,
        CHOICE,
        SCALE
    }

    private record InputParameter(String name, int index) {}

    private record QuestionMapping(String name, Question question, Kind kind, Class<?> enumType) {

        Object value(DecisionResponse response, DoubleSupplier threshold) {
            return switch (kind) {
                case BOOLEAN -> response.yesNo(name).isYes(threshold.getAsDouble());
                case YES_NO -> response.yesNo(name);
                case ENUM -> constant(response.choice(name).value());
                case CHOICE -> choice(response.choice(name));
                case SCALE -> scale(response.scale(name));
            };
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Enum constant(String option) {
            try {
                return Enum.valueOf((Class) enumType, option);
            } catch (IllegalArgumentException e) {
                throw new InvalidDecisionResponseException("The model chose '%s' for '%s', which is not a constant of %s"
                        .formatted(option, name, enumType.getSimpleName()));
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Scale scale(ScaleAnswer answer) {
            Object[] levels = enumType.getEnumConstants();
            List<Double> reported = answer.probabilities();
            Map probabilities = new EnumMap(enumType);
            for (int i = 0; i < reported.size(); i++) {
                probabilities.put(levels[i], reported.get(i));
            }
            return Scale.builder((Class) enumType)
                    .mean(answer.mean())
                    .probabilities(probabilities)
                    .confidence(answer.confidence())
                    .build();
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Choice choice(ChoiceAnswer answer) {
            Map probabilities = new EnumMap(enumType);
            answer.probabilities().forEach((option, probability) -> probabilities.put(constant(option), probability));
            return Choice.builder()
                    .value(constant(answer.value()))
                    .probabilities(probabilities)
                    .confidence(answer.confidence())
                    .build();
        }
    }
}
