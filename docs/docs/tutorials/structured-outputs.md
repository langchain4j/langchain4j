---
sidebar_position: 11
---

# Structured Outputs

:::note
The term "Structured Outputs" is overloaded and can refer to two things:
- The general ability of the LLM to generate outputs in a structured format (this is what we cover on this page)
- The [Structured Outputs](https://platform.openai.com/docs/guides/structured-outputs) feature of OpenAI,
which applies to both response format and tools (function calling).
:::

Many LLMs and LLM providers support generating outputs in a structured format, typically JSON.
These outputs can be easily mapped to Java objects and used in other parts of your application.

For instance, let’s assume we have a `Person` class:
```java
record Person(String name, int age, double height, boolean married) {
}
```
We aim to extract a `Person` object from unstructured text like this:
```
John is 42 years old and lives an independent life.
He stands 1.75 meters tall and carries himself with confidence.
Currently unmarried, he enjoys the freedom to focus on his personal goals and interests.
```

Currently, depending on the LLM and the LLM provider, there are three ways how this can be achieved
(from most to least reliable):
- [JSON Schema](/tutorials/structured-outputs#json-schema)
- [Prompting + JSON Mode](/tutorials/structured-outputs#prompting--json-mode)
- [Prompting](/tutorials/structured-outputs#prompting)


## JSON Schema
Some LLM providers (currently Amazon Bedrock, Azure OpenAI, Google AI Gemini, Mistral, Ollama and OpenAI) allow
specifying [JSON schema](https://json-schema.org/overview/what-is-jsonschema) for the desired output.
You can view all supported LLM providers [here](/integrations/language-models) in the "JSON Schema" column.

When a JSON schema is specified in the request, the LLM is expected to generate an output that adheres to this schema.

:::note
Please note that the JSON schema is specified in a dedicated attribute in the request to the LLM provider's API
and does not require any free-form instructions to be included in the prompt (e.g., in system or user messages).
:::

LangChain4j supports the JSON Schema feature in both the low-level `ChatModel` API
and the high-level AI Service API.

### Using JSON Schema with `ChatModel`

In the low-level `ChatModel` API, JSON schema can be specified
using LLM-provider-agnostic `ResponseFormat` and `JsonSchema` when creating a `ChatRequest`:
```java
ResponseFormat responseFormat = ResponseFormat.builder()
        .type(JSON) // type can be either TEXT (default) or JSON
        .jsonSchema(JsonSchema.builder()
                .name("Person") // OpenAI requires specifying the name for the schema
                .rootElement(JsonObjectSchema.builder() // see [1] below
                        .addStringProperty("name")
                        .addIntegerProperty("age")
                        .addNumberProperty("height")
                        .addBooleanProperty("married")
                        .required("name", "age", "height", "married") // see [2] below
                        .build())
                .build())
        .build();

UserMessage userMessage = UserMessage.from("""
        John is 42 years old and lives an independent life.
        He stands 1.75 meters tall and carries himself with confidence.
        Currently unmarried, he enjoys the freedom to focus on his personal goals and interests.
        """);

ChatRequest chatRequest = ChatRequest.builder()
        .responseFormat(responseFormat)
        .messages(userMessage)
        .build();

ChatModel chatModel = OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4o-mini")
        .logRequests(true)
        .logResponses(true)
        .build();
// OR
ChatModel chatModel = AzureOpenAiChatModel.builder()
        .endpoint(System.getenv("AZURE_OPENAI_URL"))
        .apiKey(System.getenv("AZURE_OPENAI_API_KEY"))
        .deploymentName("gpt-4o-mini")
        .logRequestsAndResponses(true)
        .build();
// OR
ChatModel chatModel = GoogleAiGeminiChatModel.builder()
        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
        .modelName("gemini-1.5-flash")
        .logRequestsAndResponses(true)
        .build();
// OR
ChatModel chatModel = OllamaChatModel.builder()
        .baseUrl("http://localhost:11434")
        .modelName("llama3.1")
        .logRequests(true)
        .logResponses(true)
        .build();
// OR
ChatModel chatModel = MistralAiChatModel.builder()
        .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
        .modelName("mistral-small-latest")
        .logRequests(true)
        .logResponses(true)
        .build();
// OR
ChatModel chatModel = WatsonxChatModel.builder()
        .baseUrl(System.getenv("WATSONX_URL"))
        .projectId(System.getenv("WATSONX_PROJECT_ID"))
        .apiKey(System.getenv("WATSONX_API_KEY"))
        .modelName("ibm/granite-4-h-small")
        .logRequests(true)
        .logResponses(true)
        .build();
// OR
ChatModel chatModel = BedrockChatModel.builder()
        .modelId("us.anthropic.claude-haiku-4-5-20251001-v1:0")
        .logRequests(true)
        .logResponses(true)
        .build();

ChatResponse chatResponse = chatModel.chat(chatRequest);

String output = chatResponse.aiMessage().text();
System.out.println(output); // {"name":"John","age":42,"height":1.75,"married":false}

Person person = new ObjectMapper().readValue(output, Person.class);
System.out.println(person); // Person[name=John, age=42, height=1.75, married=false]
```
Notes:
- [1] - In most cases, the root element must be of `JsonObjectSchema` type,
however:
  - Amazon Bedrock, Azure OpenAI, Mistral, Ollama, OpenAI and OpenAI Official also allow `JsonRawSchema` as a root element
  - Gemini also allows `JsonEnumSchema` and `JsonArraySchema` as root elements
- [2] - Required properties must be explicitly specified; otherwise, they are considered optional.

The structure of the JSON schema is defined using `JsonSchemaElement` interface,
with the following subtypes:
- `JsonObjectSchema` - for object types.
- `JsonStringSchema` - for `String`, `char`/`Character` types.
- `JsonIntegerSchema` - for `int`/`Integer`, `long`/`Long`, `BigInteger` types.
- `JsonNumberSchema` - for `float`/`Float`, `double`/`Double`, `BigDecimal` types.
- `JsonBooleanSchema` - for `boolean`/`Boolean` types.
- `JsonEnumSchema` - for `enum` types.
- `JsonArraySchema` - for arrays and collections (e.g., `List`, `Set`).
- `JsonReferenceSchema` - to support recursion (e.g., `Person` has a `Set<Person> children` field).
- `JsonAnyOfSchema` - to support polymorphism (e.g., `Shape` can be either `Circle` or `Rectangle`).
- `JsonNullSchema` - to support nullable type.
- `JsonRawSchema` - to use your custom fully defined JSON schema.

#### `JsonObjectSchema`

The `JsonObjectSchema` represents an object with nested properties.
It is usually the root element of the `JsonSchema`.

There are several ways to add properties to a `JsonObjectSchema`:
1. You can add all the properties at once using the `properties(Map<String, JsonSchemaElement> properties)` method:
```java
JsonSchemaElement citySchema = JsonStringSchema.builder()
        .description("The city for which the weather forecast should be returned")
        .build();

JsonSchemaElement temperatureUnitSchema = JsonEnumSchema.builder()
        .enumValues("CELSIUS", "FAHRENHEIT")
        .build();

Map<String, JsonSchemaElement> properties = Map.of(
        "city", citySchema,
        "temperatureUnit", temperatureUnitSchema
);

JsonSchemaElement rootElement = JsonObjectSchema.builder()
        .addProperties(properties)
        .required("city") // required properties should be specified explicitly
        .build();
```

2. You can add properties individually using the `addProperty(String name, JsonSchemaElement jsonSchemaElement)` method:
```java
JsonSchemaElement rootElement = JsonObjectSchema.builder()
        .addProperty("city", citySchema)
        .addProperty("temperatureUnit", temperatureUnitSchema)
        .required("city")
        .build();
```

3. You can add properties individually using one of the `add{Type}Property(String name)` or `add{Type}Property(String name, String description)` methods:
```java
JsonSchemaElement rootElement = JsonObjectSchema.builder()
        .addStringProperty("city", "The city for which the weather forecast should be returned")
        .addEnumProperty("temperatureUnit", List.of("CELSIUS", "FAHRENHEIT"))
        .required("city")
        .build();
```

Please refer to the Javadoc of the 
[JsonObjectSchema](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/model/chat/request/json/JsonObjectSchema.java)
for more details.

#### `JsonStringSchema`

An example of creating `JsonStringSchema`:
```java
JsonSchemaElement stringSchema = JsonStringSchema.builder()
        .description("The name of the person")
        .build();
```

#### `JsonIntegerSchema`

An example of creating `JsonIntegerSchema`:
```java
JsonSchemaElement integerSchema = JsonIntegerSchema.builder()
        .description("The age of the person")
        .build();
```

#### `JsonNumberSchema`

An example of creating `JsonNumberSchema`:
```java
JsonSchemaElement numberSchema = JsonNumberSchema.builder()
        .description("The height of the person")
        .build();
```

#### `JsonBooleanSchema`

An example of creating `JsonBooleanSchema`:
```java
JsonSchemaElement booleanSchema = JsonBooleanSchema.builder()
        .description("Is the person married?")
        .build();
```

#### `JsonEnumSchema`

An example of creating `JsonEnumSchema`:
```java
JsonSchemaElement enumSchema = JsonEnumSchema.builder()
        .description("Marital status of the person")
        .enumValues(List.of("SINGLE", "MARRIED", "DIVORCED"))
        .build();
```

#### `JsonArraySchema`

An example of creating `JsonArraySchema` to define an array of strings:
```java
JsonSchemaElement itemSchema = JsonStringSchema.builder()
        .description("The name of the person")
        .build();

JsonSchemaElement arraySchema = JsonArraySchema.builder()
        .description("All names of the people found in the text")
        .items(itemSchema)
        .build();
```

#### `JsonReferenceSchema`

The `JsonReferenceSchema` can be used to support recursion:
```java
String reference = "person"; // reference should be unique withing the schema

JsonObjectSchema jsonObjectSchema = JsonObjectSchema.builder()
        .addStringProperty("name")
        .addProperty("children", JsonArraySchema.builder()
                .items(JsonReferenceSchema.builder()
                        .reference(reference)
                        .build())
                .build())
        .required("name", "children")
        .definitions(Map.of(reference, JsonObjectSchema.builder()
                .addStringProperty("name")
                .addProperty("children", JsonArraySchema.builder()
                        .items(JsonReferenceSchema.builder()
                                .reference(reference)
                                .build())
                        .build())
                .required("name", "children")
                .build()))
        .build();
```

:::note
The `JsonReferenceSchema` is currently supported only by Azure OpenAI, Mistral and OpenAI.
:::

#### `JsonAnyOfSchema`

The `JsonAnyOfSchema` can be used to support polymorphism:
```java
JsonSchemaElement circleSchema = JsonObjectSchema.builder()
        .addNumberProperty("radius")
        .build();

JsonSchemaElement rectangleSchema = JsonObjectSchema.builder()
        .addNumberProperty("width")
        .addNumberProperty("height")
        .build();

JsonSchemaElement shapeSchema = JsonAnyOfSchema.builder()
        .anyOf(circleSchema, rectangleSchema)
        .build();

JsonSchema jsonSchema = JsonSchema.builder()
        .name("Shapes")
        .rootElement(JsonObjectSchema.builder()
                .addProperty("shapes", JsonArraySchema.builder()
                        .items(shapeSchema)
                        .build())
                .required(List.of("shapes"))
                .build())
        .build();

ResponseFormat responseFormat = ResponseFormat.builder()
        .type(ResponseFormatType.JSON)
        .jsonSchema(jsonSchema)
        .build();

UserMessage userMessage = UserMessage.from("""
        Extract information from the following text:
        1. A circle with a radius of 5
        2. A rectangle with a width of 10 and a height of 20
        """);

ChatRequest chatRequest = ChatRequest.builder()
        .messages(userMessage)
        .responseFormat(responseFormat)
        .build();

ChatResponse chatResponse = model.chat(chatRequest);

System.out.println(chatResponse.aiMessage().text()); // {"shapes":[{"radius":5},{"width":10,"height":20}]}
```

:::note
The `JsonAnyOfSchema` is currently supported only by OpenAI, Azure OpenAI and Google AI Gemini.
:::

#### `JsonRawSchema`

An example of creating a `JsonRawSchema` from an existing schema string:

```java
var rawSchema = """
{
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
        "city": {
            "type": "string"
        }
    },
    "required": ["city"],
    "additionalProperties": false
}
""";

JsonRawSchema schema = JsonRawSchema.from(rawSchema);
```

:::note
The `JsonRawSchema` is currently supported only by Amazon Bedrock, Azure OpenAI, Mistral, Ollama, OpenAI, OpenAI Official and Google AI Gemini.
For Google AI Gemini specifically, see the example in the [Response JSON Schema](/integrations/language-models/google-ai-gemini/#response-json-schema).
:::


#### Adding Description

All of the `JsonSchemaElement` subtypes, except for `JsonReferenceSchema`, have a `description` property.
If an LLM does not provide the desired output, descriptions can be provided
to give more instructions and examples of correct outputs to the LLM, for example:
```java
JsonSchemaElement stringSchema = JsonStringSchema.builder()
        .description("The name of the person, for example: John Doe")
        .build();
```

#### Limitations

When using JSON Schema with `ChatModel`, there are some limitations:
- It works only with supported Amazon Bedrock, Azure OpenAI, Google AI Gemini, Mistral, Ollama and OpenAI models.
- It does not work in the [streaming mode](/tutorials/ai-services#streaming) for OpenAI yet.
For Google AI Gemini, Mistral and Ollama, JSON Schema can be specified via `responseSchema(...)` when creating/building the model.
- `JsonReferenceSchema` and `JsonAnyOfSchema` are currently supported only by Azure OpenAI, Mistral and OpenAI.


### Using JSON Schema with AI Services

When using [AI Services](/tutorials/ai-services), one can achieve the same much easier and with less code:
```java
interface PersonExtractor {
    
    Person extractPersonFrom(String text);
}

ChatModel chatModel = OpenAiChatModel.builder() // see [1] below
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4o-mini")
        .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA) // see [2] below
        .strictJsonSchema(true) // see [2] below
        .logRequests(true)
        .logResponses(true)
        .build();
// OR
ChatModel chatModel = AzureOpenAiChatModel.builder() // see [1] below
        .endpoint(System.getenv("AZURE_OPENAI_URL"))
        .apiKey(System.getenv("AZURE_OPENAI_API_KEY"))
        .deploymentName("gpt-4o-mini")
        .strictJsonSchema(true)
        .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA) // see [3] below
        .logRequestsAndResponses(true)
        .build();
// OR
ChatModel chatModel = GoogleAiGeminiChatModel.builder() // see [1] below
        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
        .modelName("gemini-1.5-flash")
        .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA) // see [4] below
        .logRequestsAndResponses(true)
        .build();
// OR
ChatModel chatModel = OllamaChatModel.builder() // see [1] below
        .baseUrl("http://localhost:11434")
        .modelName("llama3.1")
        .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA) // see [5] below
        .logRequests(true)
        .logResponses(true)
        .build();
// OR
ChatModel chatModel = MistralAiChatModel.builder()
         .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
         .modelName("mistral-small-latest")
         .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA) // see [6] below
         .strictJsonSchema(true) // see [6] below
         .logRequests(true)
         .logResponses(true)
         .build();
// OR
ChatModel chatModel = WatsonxChatModel.builder()
        .baseUrl(System.getenv("WATSONX_URL"))
        .projectId(System.getenv("WATSONX_PROJECT_ID"))
        .apiKey(System.getenv("WATSONX_API_KEY"))
        .modelName("ibm/granite-4-h-small")
        .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA) // see [7] below
        .logRequests(true)
        .logResponses(true)
        .build();
// OR
ChatModel chatModel = BedrockChatModel.builder()
        .modelId("us.anthropic.claude-haiku-4-5-20251001-v1:0")
        .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA) // see [8] below
        .logRequests(true)
        .logResponses(true)
        .build();

PersonExtractor personExtractor = AiServices.create(PersonExtractor.class, chatModel); // see [1] below

String text = """
        John is 42 years old and lives an independent life.
        He stands 1.75 meters tall and carries himself with confidence.
        Currently unmarried, he enjoys the freedom to focus on his personal goals and interests.
        """;

Person person = personExtractor.extractPersonFrom(text);

System.out.println(person); // Person[name=John, age=42, height=1.75, married=false]
```
Notes:
- [1] - In a Quarkus or a Spring Boot application, there is no need to explicitly create the `ChatModel` and the AI Service,
as these beans are created automatically. More info on this:
[for Quarkus](https://docs.quarkiverse.io/quarkus-langchain4j/dev/ai-services.html),
[for Spring Boot](https://docs.langchain4j.dev/tutorials/spring-boot-integration#spring-boot-starter-for-declarative-ai-services).
- [2] - This is required to enable the JSON Schema feature for OpenAI, see more details [here](/integrations/language-models/open-ai#structured-outputs-for-response-format).
- [3] - This is required to enable the JSON Schema feature for [Azure OpenAI](/integrations/language-models/azure-open-ai).
- [4] - This is required to enable the JSON Schema feature for [Google AI Gemini](/integrations/language-models/google-ai-gemini).
- [5] - This is required to enable the JSON Schema feature for [Ollama](/integrations/language-models/ollama).
- [6] - This is required to enable the JSON Schema feature for [Mistral](/integrations/language-models/mistral-ai).
- [7] - This is required to enable the JSON Schema feature for [watsonx.ai](/integrations/language-models/watsonx).
- [8] - This is required to enable the JSON Schema feature for [Amazon Bedrock](/integrations/language-models/amazon-bedrock).

When all the following conditions are met:
- AI Service method returns a POJO
- The used `ChatModel` [supports](https://docs.langchain4j.dev/integrations/language-models/) the JSON Schema feature
- The JSON Schema feature is enabled on the used `ChatModel`

then the `ResponseFormat` with `JsonSchema` will be generated automatically based on the specified return type.

:::note
Make sure to explicitly enable JSON Schema feature when configuring `ChatModel`,
as it is disabled by default.
:::

The `name` of the generated `JsonSchema` is a simple name of the return type (`getClass().getSimpleName()`),
in this case: "Person".

Once LLM responds, the output is parsed into an object and returned from the AI Service method.

You can find many examples of supported use cases
[here](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/test/java/dev/langchain4j/service/AiServicesWithJsonSchemaIT.java)
and [here](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/test/java/dev/langchain4j/service/AiServicesWithJsonSchemaWithDescriptionsIT.java).

#### Required and Optional

By default, all fields and sub-fields in the generated `JsonSchema` are considered **_optional_**.
This is because LLMs tend to hallucinate and populate fields with synthetic data when they
lack sufficient information (e.g., using "John Doe" when then name is missing)".

:::note
Please note that optional fields with primitive types (e.g., `int`, `boolean`, etc.)
will be initialized with default values (e.g., `0` for `int`, `false` for `boolean`, etc.)
if the LLM does not provide a value for them.
:::

:::note
Please note that optional `enum` fields can still be populated with hallucinated values
when strict mode is on (`strictJsonSchema(true)`).
:::

To make the field required, you can annotate it with `@JsonProperty(required = true)`:
```java
record Person(@JsonProperty(required = true) String name, String surname) {
}

interface PersonExtractor {
    
    Person extractPersonFrom(String text);
}
```

:::note
Please note that when used with [tools](/tutorials/tools),
all fields and sub-fields are considered **_required_** by default.
:::

#### Adding Description

If an LLM does not provide the desired output, classes and fields can be annotated with `@Description`
to give more instructions and examples of correct outputs to the LLM, for example:
```java
@Description("a person")
record Person(@Description("person's first and last name, for example: John Doe") String name,
              @Description("person's age, for example: 42") int age,
              @Description("person's height in meters, for example: 1.78") double height,
              @Description("is person married or not, for example: false") boolean married) {
}
```

:::note
Please note that `@Description` placed on an `enum` value has **_no effect_** and **_is not_** included
in the generated JSON schema:
```java
enum Priority {

    @Description("Critical issues such as payment gateway failures or security breaches.") // this is ignored
    CRITICAL,
    
    @Description("High-priority issues like major feature malfunctions or widespread outages.") // this is ignored
    HIGH,
    
    @Description("Low-priority issues such as minor bugs or cosmetic problems.") // this is ignored
    LOW
}
```
:::

#### Polymorphic Types

AI Service methods can return polymorphic types — a base type whose concrete subtype is
decided by the LLM at runtime. Two flavors are supported:

- **Sealed interfaces and sealed classes** — no annotations needed; subtypes are
  discovered via `Class.getPermittedSubclasses()`.
- **Plain abstract classes and interfaces** — must declare their subtypes explicitly
  with Jackson's `@JsonSubTypes`.

Polymorphic return types work for the type itself, for collections (`List<T>`, `Set<T>`),
for fields nested inside other POJOs, and for recursive hierarchies where a subtype
contains the base type as a field (e.g., `BinaryOp(left: ExpressionNode, right: ExpressionNode)`
where `ExpressionNode` is the sealed base).

A discriminator property (defaulting to `"type"`) is added to each subtype so the LLM
can communicate which concrete type it produced; the parser then dispatches to the
right subtype automatically.

**Sealed interfaces and classes — no annotations needed:**

```java
sealed interface Animal permits Dog, Cat {}

record Dog(String name, String breed) implements Animal {}

record Cat(String name, boolean indoor) implements Animal {}

interface AnimalExtractor {

    Animal extractAnimalFrom(String text);
}
```

The LLM is shown a schema with `anyOf` over `Dog` and `Cat`, each constrained to emit
its simple class name in the `type` property. Given:

```
Rex is a Labrador.
```

the LLM emits `{"value":{"type":"Dog","name":"Rex","breed":"Labrador"}}`, which is parsed
back into a `Dog` instance.

:::note
Because JSON schemas cannot have `anyOf` at the root, the schema wraps the polymorphic
choice under a `value` property (or `values` for collections). The wrapper is an
implementation detail — your AI Service method still returns the unwrapped subtype.
:::

**Collections of polymorphic types:**

```java
interface AnimalsExtractor {

    List<Animal> extractAnimalsFrom(String text);
}
```

**Polymorphic fields inside another POJO:**

```java
record Owner(String name, Animal pet) {}

interface OwnerExtractor {

    Owner extractOwnerFrom(String text);
}
```

**Jackson `@JsonSubTypes` / `@JsonTypeInfo`** are also supported and let you decouple wire
names from Java class names:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Square.class, name = "square"),
    @JsonSubTypes.Type(value = Circle.class, name = "circle")
})
interface Shape {}

class Square implements Shape { double side; }

class Circle implements Shape { double radius; }
```

Resolution order for the discriminator value:
1. `@JsonSubTypes.Type(name = "...")` on the base type
2. `@JsonTypeName` on the subtype
3. `Class.getSimpleName()` (the default)

So `@JsonTypeName` is a convenient way to set a wire name when you don't want to declare
it on the base:

```java
sealed interface Bird permits Eagle, Sparrow {}

@JsonTypeName("eagle")
record Eagle(double wingspanMeters) implements Bird {}

@JsonTypeName("sparrow")
record Sparrow(boolean migratory) implements Bird {}
```

The LLM will see `"eagle"` / `"sparrow"` as the discriminator values rather than the simple
class names.

**Supported `@JsonTypeInfo` configuration:**

| Attribute | Supported values |
|---|---|
| `use` | `Id.NAME`, `Id.SIMPLE_NAME` |
| `include` | `As.PROPERTY` (default), `As.EXISTING_PROPERTY` |
| `property` | Any explicit value; defaults to `"@type"` when blank |
| `defaultImpl` | Any concrete subclass — used when the LLM's discriminator is missing or unknown |
| `visible` | `true` keeps the discriminator field on the deserialized bean (and bypasses the field-collision check) |

Anything else (e.g., `Id.CLASS`, `As.WRAPPER_OBJECT`) is rejected at schema-generation time
with an `UnsupportedFeatureException`.

**`defaultImpl` for hallucination tolerance:**

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, defaultImpl = UnknownTool.class)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Hammer.class, name = "hammer"),
    @JsonSubTypes.Type(value = Wrench.class, name = "wrench")
})
interface Tool {}
```

If the LLM emits an unknown discriminator (e.g., `"saw"`) or omits it entirely, the parser
returns an `UnknownTool` instead of failing, so your code can detect and handle the
hallucination.

**Adding descriptions:**

You can guide the LLM by annotating the base type and/or subtypes with `@Description`.
The base-type description is attached to the `anyOf` element, and each subtype's
description is attached to its individual option:

```java
@Description("A pet that lives in your home")
sealed interface Pet permits Hamster, Parrot {}

@Description("A small caged rodent kept as a pet")
record Hamster(String name, double weightGrams) implements Pet {}

@Description("A talking bird that can mimic human speech")
record Parrot(String name, int vocabulary) implements Pet {}
```

When `@Description` is omitted, descriptions fall back to the simple class name
(e.g., `"Hamster"`) so the LLM still has a label per option.

**Recursive polymorphic types:**

A polymorphic base whose subtypes contain it as a field works out of the box:

```java
sealed interface ExpressionNode permits Literal, BinaryOp {}

record Literal(int value) implements ExpressionNode {}

record BinaryOp(String operator, ExpressionNode left, ExpressionNode right) implements ExpressionNode {}
```

Recursive polymorphic schemas require a model that supports `$ref` / `$defs`
(currently OpenAI, Azure OpenAI, and Google AI Gemini in strict mode).

**Discriminator field collisions:**

If a subtype declares a field with the same name as the discriminator (e.g., a `type` field
on a sealed-only base), schema generation fails with a clear message. Fix options:

- Rename the field, or
- Choose a different discriminator name with `@JsonTypeInfo(property = "...")`, or
- Set `@JsonTypeInfo(visible = true)` if the field is intentionally part of the subtype, or
- Use `@JsonTypeInfo(include = As.EXISTING_PROPERTY)` when the field on the subtype is the
  source of truth for the discriminator.

#### Limitations

When using JSON Schema with AI Services, there are some limitations:
- It works only with supported Amazon Bedrock, Azure OpenAI, Google AI Gemini, Mistral, Ollama and OpenAI models.
- Support for JSON Schema needs to be enabled explicitly when configuring `ChatModel`.
- It does not work in the [streaming mode](/tutorials/ai-services#streaming).
- Not all types are supported. See the list of supported types [here](/tutorials/structured-outputs#supported-types).
- POJOs can contain:
  - Scalar/simple types (e.g., `String`, `int`/`Integer`, `double`/`Double`, `boolean`/`Boolean`, etc.)
  - `enum`s
  - Nested POJOs
  - `List<T>`, `Set<T>` and `T[]`, where `T` is a scalar, an `enum` or a POJO
  - Polymorphic types (sealed interfaces/classes or types annotated with Jackson `@JsonSubTypes`)
- Recursion is currently supported only by Azure OpenAI, Mistral and OpenAI.
- Polymorphic types require an LLM that supports `anyOf` in the JSON schema.
- When LLM does not support JSON Schema feature, or it is not enabled, or type is not supported,
  AI Service will fall back to [prompting](/tutorials/structured-outputs#prompting).


## Prompting + JSON Mode

More info is coming soon.
In the meantime, please read [this section](/tutorials/ai-services#json-mode)
and [this article](https://glaforge.dev/posts/2024/11/18/data-extraction-the-many-ways-to-get-llms-to-spit-json-content/).


## Prompting

When using prompting (this is a default choice, unless support for JSON schema is enabled),
AI Service will automatically generate format instructions and append them to the end of the `UserMessage`
indicating the format in which the LLM should respond.
Before the method returns, the AI Service will parse the output of the LLM into the desired type.

You can observe appended instructions by [enabling logging](/tutorials/logging).

:::note
This approach is quite unreliable.
If LLM and LLM provider supports the methods described above, it is better to use those.
:::


## Supported Types

| Type                                                       | JSON Schema | Prompting |
|------------------------------------------------------------|-------------|-----------|
| `POJO`                                                     | ✅           | ✅         |
| `List<POJO>`, `Set<POJO>`                                  | ✅           | ❌         |
| `Enum`                                                     | ✅           | ✅         |
| `List<Enum>`, `Set<Enum>`                                  | ✅           | ✅         |
| `List<String>`, `Set<String>`                              | ✅           | ✅         |
| Polymorphic (sealed / `@JsonSubTypes`), incl. `List`/`Set` | ✅           | ❌         |
| `boolean`, `Boolean`                                       | ✅           | ✅         |
| `int`, `Integer`                                           | ✅           | ✅         |
| `long`, `Long`                                             | ✅           | ✅         |
| `float`, `Float`                                           | ✅           | ✅         |
| `double`, `Double`                                         | ✅           | ✅         |
| `byte`, `Byte`                                             | ❌           | ✅         |
| `short`, `Short`                                           | ❌           | ✅         |
| `BigInteger`                                               | ❌           | ✅         |
| `BigDecimal`                                               | ❌           | ✅         |
| `Date`                                                     | ❌           | ✅         |
| `LocalDate`                                                | ❌           | ✅         |
| `LocalTime`                                                | ❌           | ✅         |
| `LocalDateTime`                                            | ❌           | ✅         |
| `Map<?, ?>`                                                | ❌           | ✅         |

A few examples:
```java
record Person(String firstName, String lastName) {}

enum Sentiment {
    POSITIVE, NEGATIVE, NEUTRAL
}

interface Assistant {

    Person extractPersonFrom(String text);

    Set<Person> extractPeopleFrom(String text);

    Sentiment extractSentimentFrom(String text);

    List<Sentiment> extractSentimentsFrom(String text);

    List<String> generateOutline(String topic);

    boolean isSentimentPositive(String text);

    Integer extractNumberOfPeopleMentionedIn(String text);
}
```

## Related Tutorials
- [Data extraction: The many ways to get LLMs to spit JSON content](https://glaforge.dev/posts/2024/11/18/data-extraction-the-many-ways-to-get-llms-to-spit-json-content/) by [Guillaume Laforge](https://glaforge.dev/about/)
