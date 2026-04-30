package dev.langchain4j.service.common.openai;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

// TODO move to langchain4j-open-ai module once dependency cycle is resolved
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiAiServiceWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    OpenAiChatModel modelWithStrictJsonSchema = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
            .strictJsonSchema(true)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    OpenAiChatModel modelWithStrictJsonSchemaLegacy = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .responseFormat("json_schema") // testing backward compatibility
            .strictJsonSchema(true)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(
                modelWithStrictJsonSchema,
                modelWithStrictJsonSchemaLegacy,
                OpenAiChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .modelName(GPT_4_O_MINI)
                        .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                        .strictJsonSchema(false)
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build(),
                OpenAiChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .modelName(GPT_4_O_MINI)
                        .responseFormat("json_schema") // testing backward compatibility
                        .strictJsonSchema(false)
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build());
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }

    @Override
    protected boolean isStrictJsonSchemaEnabled(ChatModel model) {
        return model == modelWithStrictJsonSchema || model == modelWithStrictJsonSchemaLegacy;
    }

    sealed interface Animal permits Dog, Cat {
    }

    record Dog(String name, String breed) implements Animal {
    }

    record Cat(String name, boolean indoor) implements Animal {
    }

    interface AnimalExtractor {
        Animal extractAnimalFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_extract_polymorphic_sealed_type(ChatModel model) {

        // given
        model = spy(model);

        AnimalExtractor extractor = AiServices.create(AnimalExtractor.class, model);

        String text = "Rex is a Labrador dog";

        // when
        Animal animal = extractor.extractAnimalFrom(text);

        // then
        assertThat(animal).isInstanceOf(Dog.class);
        Dog dog = (Dog) animal;
        assertThat(dog.name()).isEqualToIgnoringCase("Rex");
        assertThat(dog.breed()).containsIgnoringCase("Labrador");

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Animal")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addProperty(
                                                        "value",
                                                        JsonAnyOfSchema.builder()
                                                                .description("Animal")
                                                                .anyOf(List.of(
                                                                        JsonObjectSchema.builder()
                                                                                .description("Dog")
                                                                                .addProperty(
                                                                                        "type",
                                                                                        JsonEnumSchema.builder()
                                                                                                .enumValues("Dog")
                                                                                                .build())
                                                                                .addStringProperty("name")
                                                                                .addStringProperty("breed")
                                                                                .required("type")
                                                                                .build(),
                                                                        JsonObjectSchema.builder()
                                                                                .description("Cat")
                                                                                .addProperty(
                                                                                        "type",
                                                                                        JsonEnumSchema.builder()
                                                                                                .enumValues("Cat")
                                                                                                .build())
                                                                                .addStringProperty("name")
                                                                                .addBooleanProperty("indoor")
                                                                                .required("type")
                                                                                .build()))
                                                                .build())
                                                .required("value")
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    interface AnimalsExtractor {
        List<Animal> extractAnimalsFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_extract_list_of_polymorphic_sealed_type(ChatModel model) {

        // given
        model = spy(model);

        AnimalsExtractor extractor = AiServices.create(AnimalsExtractor.class, model);

        String text = "Rex is a Labrador. Whiskers is an indoor cat.";

        // when
        List<Animal> animals = extractor.extractAnimalsFrom(text);

        // then
        assertThat(animals).hasSize(2);
        assertThat(animals).hasAtLeastOneElementOfType(Dog.class);
        assertThat(animals).hasAtLeastOneElementOfType(Cat.class);

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("List_of_Animal")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addProperty(
                                                        "values",
                                                        JsonArraySchema.builder()
                                                                .items(JsonAnyOfSchema.builder()
                                                                        .description("Animal")
                                                                        .anyOf(List.of(
                                                                                JsonObjectSchema.builder()
                                                                                        .description("Dog")
                                                                                        .addProperty(
                                                                                                "type",
                                                                                                JsonEnumSchema.builder()
                                                                                                        .enumValues(
                                                                                                                "Dog")
                                                                                                        .build())
                                                                                        .addStringProperty("name")
                                                                                        .addStringProperty("breed")
                                                                                        .required("type")
                                                                                        .build(),
                                                                                JsonObjectSchema.builder()
                                                                                        .description("Cat")
                                                                                        .addProperty(
                                                                                                "type",
                                                                                                JsonEnumSchema.builder()
                                                                                                        .enumValues(
                                                                                                                "Cat")
                                                                                                        .build())
                                                                                        .addStringProperty("name")
                                                                                        .addBooleanProperty("indoor")
                                                                                        .required("type")
                                                                                        .build()))
                                                                        .build())
                                                                .build())
                                                .required("values")
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Square.class, name = "square"),
            @JsonSubTypes.Type(value = Circle.class, name = "circle")
    })
    interface Shape {
    }

    static class Square implements Shape {
        double side;
    }

    static class Circle implements Shape {
        double radius;
    }

    interface ShapeExtractor {
        Shape extractShapeFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_extract_polymorphic_jackson_annotated_type(ChatModel model) {

        // given
        model = spy(model);

        ShapeExtractor extractor = AiServices.create(ShapeExtractor.class, model);

        String text = "A circle with radius 2.5";

        // when
        Shape shape = extractor.extractShapeFrom(text);

        // then
        assertThat(shape).isInstanceOf(Circle.class);
        assertThat(((Circle) shape).radius).isEqualTo(2.5);

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Shape")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addProperty(
                                                        "value",
                                                        JsonAnyOfSchema.builder()
                                                                .description("Shape")
                                                                .anyOf(List.of(
                                                                        JsonObjectSchema.builder()
                                                                                .description("Square")
                                                                                .addProperty(
                                                                                        "kind",
                                                                                        JsonEnumSchema.builder()
                                                                                                .enumValues("square")
                                                                                                .build())
                                                                                .addNumberProperty("side")
                                                                                .required("kind")
                                                                                .build(),
                                                                        JsonObjectSchema.builder()
                                                                                .description("Circle")
                                                                                .addProperty(
                                                                                        "kind",
                                                                                        JsonEnumSchema.builder()
                                                                                                .enumValues("circle")
                                                                                                .build())
                                                                                .addNumberProperty("radius")
                                                                                .required("kind")
                                                                                .build()))
                                                                .build())
                                                .required("value")
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    record Owner(String name, Animal pet) {}

    interface OwnerExtractor {
        Owner extractOwnerFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_extract_pojo_with_nested_polymorphic_field(ChatModel model) {

        // given
        model = spy(model);

        OwnerExtractor extractor = AiServices.create(OwnerExtractor.class, model);

        String text = "Alice owns a Labrador dog named Rex.";

        // when
        Owner owner = extractor.extractOwnerFrom(text);

        // then
        assertThat(owner.name()).isEqualToIgnoringCase("Alice");
        assertThat(owner.pet()).isInstanceOf(Dog.class);
        Dog dog = (Dog) owner.pet();
        assertThat(dog.name()).isEqualToIgnoringCase("Rex");
        assertThat(dog.breed()).containsIgnoringCase("Labrador");

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Owner")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addStringProperty("name")
                                                .addProperty(
                                                        "pet",
                                                        JsonAnyOfSchema.builder()
                                                                .description("Animal")
                                                                .anyOf(List.of(
                                                                        JsonObjectSchema.builder()
                                                                                .description("Dog")
                                                                                .addProperty(
                                                                                        "type",
                                                                                        JsonEnumSchema.builder()
                                                                                                .enumValues("Dog")
                                                                                                .build())
                                                                                .addStringProperty("name")
                                                                                .addStringProperty("breed")
                                                                                .required("type")
                                                                                .build(),
                                                                        JsonObjectSchema.builder()
                                                                                .description("Cat")
                                                                                .addProperty(
                                                                                        "type",
                                                                                        JsonEnumSchema.builder()
                                                                                                .enumValues("Cat")
                                                                                                .build())
                                                                                .addStringProperty("name")
                                                                                .addBooleanProperty("indoor")
                                                                                .required("type")
                                                                                .build()))
                                                                .build())
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    sealed interface ArithmeticExpression permits Constant, Addition {}

    record Constant(int value) implements ArithmeticExpression {}

    record Addition(ArithmeticExpression left, ArithmeticExpression right) implements ArithmeticExpression {}

    interface ExpressionExtractor {
        ArithmeticExpression extractFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_extract_recursive_polymorphic_type(ChatModel model) {

        // given
        ExpressionExtractor extractor = AiServices.create(ExpressionExtractor.class, model);

        // when
        ArithmeticExpression expression = extractor.extractFrom(
                "Represent the literal expression 1+2+3 as a syntax tree. Do NOT simplify or evaluate. "
                        + "Use a left-associative tree: Addition(Addition(Constant(1), Constant(2)), Constant(3)).");

        // then
        assertThat(expression).isInstanceOf(Addition.class);
        List<Integer> leaves = new ArrayList<>();
        collectLeaves(expression, leaves);
        assertThat(leaves).containsExactlyInAnyOrder(1, 2, 3);
    }

    private static void collectLeaves(ArithmeticExpression expr, List<Integer> leaves) {
        if (expr instanceof Constant c) {
            leaves.add(c.value());
        } else if (expr instanceof Addition a) {
            collectLeaves(a.left(), leaves);
            collectLeaves(a.right(), leaves);
        }
    }

    /**
     * Tripwire test: documents — and proves — why the polymorphic schema generator wraps the
     * LLM-facing {@code anyOf} under a {@code value} property. OpenAI's structured-outputs API
     * currently rejects schemas whose root is not {@code type: "object"}, even though such a
     * schema is itself a perfectly valid JSON Schema. We add the {@code value} envelope to
     * satisfy that constraint.
     *
     * <p><strong>If this test starts failing</strong> (i.e., OpenAI returns 200 instead of 400
     * for a schema with {@code anyOf} at the root), it means OpenAI has relaxed the restriction
     * and the {@code value}/{@code values} envelope can be dropped from the polymorphic schema
     * shape. At that point, simplify the schema and update {@code PojoOutputParser} /
     * {@code PojoCollectionOutputParser} accordingly.</p>
     *
     * <p>Hits the raw HTTP endpoint via langchain4j's {@link HttpClient} to
     * bypass {@code OpenAiChatModel}'s client-side validation.</p>
     */
    @Test
    void openai_rejects_anyOf_at_schema_root() {

        // given
        String body =
                """
                {
                  "model": "gpt-4o-mini",
                  "messages": [{"role": "user", "content": "say hi"}],
                  "response_format": {
                    "type": "json_schema",
                    "json_schema": {
                      "name": "Animal",
                      "strict": true,
                      "schema": {
                        "anyOf": [
                          {"type": "object",
                           "properties": {"kind": {"type": "string", "enum": ["Dog"]}},
                           "required": ["kind"],
                           "additionalProperties": false},
                          {"type": "object",
                           "properties": {"kind": {"type": "string", "enum": ["Cat"]}},
                           "required": ["kind"],
                           "additionalProperties": false}
                        ]
                      }
                    }
                  }
                }
                """;

        String baseUrl = Optional.ofNullable(System.getenv("OPENAI_BASE_URL")).orElse("https://api.openai.com/v1");
        HttpRequest request = HttpRequest.builder()
                .method(HttpMethod.POST)
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + System.getenv("OPENAI_API_KEY"))
                .addHeader("Content-Type", "application/json")
                .body(body)
                .build();

        HttpClient httpClient = new LoggingHttpClient(new JdkHttpClientBuilder().build(), true, true);

        // when
        HttpException error = catchThrowableOfType(HttpException.class, () -> httpClient.execute(request));

        // then
        assertThat(error).isNotNull();
        assertThat(error.statusCode()).isEqualTo(400);
        assertThat(error.getMessage())
                .containsIgnoringCase("invalid schema")
                .containsIgnoringCase("type")
                .containsIgnoringCase("object");
    }
}
