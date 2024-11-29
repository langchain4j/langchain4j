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

Currently, depending on the LLM and the LLM provider, there are four ways how this can be achieved
(from most to least reliable):
- [JSON Schema](/tutorials/structured-outputs#json-schema)
- [Tools (Function Calling)](/tutorials/structured-outputs#tools-function-calling)
- [Prompting + JSON Mode](/tutorials/structured-outputs#prompting--json-mode)
- [Prompting](/tutorials/structured-outputs#prompting)


## JSON Schema
Some LLM providers (e.g., OpenAI and Google Gemini) allow specifying JSON schema for the desired output.
You can view all supported LLM providers [here](/integrations/language-models) in the "JSON Schema" column.

When a JSON schema is specified in the request, the LLM is expected to generate an output that adheres to this schema.

:::note
Please note that the JSON schema is specified in a dedicated attribute in the request to the LLM provider's API
and does not require additional free-form instructions to be included in the prompt (e.g., in system or user messages).
:::

:::note
When using JSON Schema and OpenAI, the [Structured Outputs](https://platform.openai.com/docs/guides/structured-outputs)
feature of OpenAI is used under the hood.
:::

LangChain4j supports the JSON Schema feature in both the low-level `ChatLanguageModel` API
and the high-level AI Service API.

### Using JSON Schema with the Low Level API

In the low-level `ChatLanguageModel` API, JSON schema can be specified
using LLM-provider-agnostic `ResponseFormat` and `JsonSchema` when creating a `ChatRequest`:
```java
ResponseFormat responseFormat = ResponseFormat.builder()
        .type(JSON) // see [1] below
        .jsonSchema(JsonSchema.builder()
                .name("Person") // see [2] below
                .rootElement(JsonObjectSchema.builder() // see [3] below
                        .addStringProperty("name")
                        .addIntegerProperty("age")
                        .addNumberProperty("height")
                        .addBooleanProperty("married")
                        .required("name", "age", "height", "married") // see [4] below
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

ChatLanguageModel chatModel = OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4o-mini")
        .logRequests(true)
        .logResponses(true)
        .build();
// OR
ChatLanguageModel chatModel = GoogleAiGeminiChatModel.builder()
        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
        .modelName("gemini-1.5-flash")
        .temperature(0.0)
        .logRequestsAndResponses(true)
        .build();

ChatResponse chatResponse = chatModel.chat(chatRequest);

String output = chatResponse.aiMessage().text();
System.out.println(output); // {"name":"John","age":42,"height":1.75,"married":false}

Person person = new ObjectMapper().readValue(output, Person.class);
System.out.println(person); // Person[name=John, age=42, height=1.75, married=false]
```
Notes:
- [1] - Response format type can be either `TEXT` (default) or `JSON`.
- [2] - OpenAI requires specifying the name for the schema.
- [3] - In most cases, the root element must be of `JsonObjectSchema` type,
however Gemini allows `JsonEnumSchema` and `JsonArraySchema` as well.
- [4] - Required properties must be explicitly specified; otherwise, they are considered optional.

The structure of the schema is defined using `JsonSchemaElement` interface,
with the following subtypes:
- `JsonStringSchema` - to support `String`, `char`/`Character` types.
- `JsonIntegerSchema` - to support `int`/`Integer`, `long`/`Long`, `BigInteger` types.
- `JsonNumberSchema` - to support `float`/`Float`, `double`/`Double`, `BigDecimal` types.
- `JsonBooleanSchema` - to support `boolean`/`Boolean` types.
- `JsonEnumSchema` - to support `enum` types.
- `JsonArraySchema` - to support arrays and collection (e.g., `List`, `Set`) types.
- `JsonObjectSchema` - to support object types.
- `JsonReferenceSchema` - to support recursion (e.g., `Person` has a `Set<Person> children` field).
- `JsonAnyOfSchema` - to support subtypes (e.g., `Shape` can be either `Circle` or `Rectangle`).

#### `JsonObjectSchema`

The `JsonObjectSchema` represents an object with nested properties.
It is usually a root element of the `JsonSchema`.

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
        .properties(properties)
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

Please refer to the Javadoc of the `JsonObjectSchema` for more details.

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
        .enumValues("SINGLE", "MARRIED", "DIVORCED") // allowed values can be set either via varargs
        .enumValues(List.of("SINGLE", "MARRIED", "DIVORCED")) // or as a list
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
The `JsonObjectSchema` is currently supported only by OpenAI.
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

JsonSchema jsonSchema = JsonSchema.builder()
        .name("Shapes")
        .rootElement(JsonObjectSchema.builder()
                .addProperty("shapes", JsonArraySchema.builder()
                        .items(JsonAnyOfSchema.builder()
                                .anyOf(circleSchema, rectangleSchema)
                                .build())
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
The `JsonReferenceSchema` is currently supported only by OpenAI.
:::

#### Adding Description
More information is coming soon.

#### Limitations
More information is coming soon.


### Using JSON Schema with AI Services

When using [AI Services](/tutorials/ai-services), one can achieve the same much easier and with less code:
```java
interface PersonExtractor {
    
    Person extractPersonFrom(String text);
}

ChatLanguageModel chatModel = OpenAiChatModel.builder() // see [1] below
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4o-mini")
        .responseFormat("json_schema") // see [2] below
        .strictJsonSchema(true) // see [2] below
        .logRequests(true)
        .logResponses(true)
        .build();
// OR
ChatLanguageModel chatModel = GoogleAiGeminiChatModel.builder() // see [1] below
        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
        .modelName("gemini-1.5-flash")
        .responseFormat(ResponseFormat.JSON) // see [3] below
        .temperature(0.0)
        .logRequestsAndResponses(true)
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
- [1] - In a Quarkus or a Spring Boot application, there is no need to explicitly create the `ChatLanguageModel` and the AI Service,
as these beans are created automatically. More info on this:
[for Quarkus](https://docs.quarkiverse.io/quarkus-langchain4j/dev/ai-services.html),
[for Spring Boot](https://docs.langchain4j.dev/tutorials/spring-boot-integration#spring-boot-starter-for-declarative-ai-services).
- [2] - This is required to enable the JSON Schema feature for OpenAI, see more details [here](/integrations/language-models/open-ai#structured-outputs-for-response-format).
- [3] - This is required to enable the JSON Schema feature for [Google AI Gemini](/integrations/language-models/google-ai-gemini).

When an AI Service returns a POJO **and** the `ChatLanguageModel` supports the JSON Schema feature
**and** the JSON Schema feature is enabled on the `ChatLanguageModel`,
a `ResponseFormat` with `JsonSchema` will be generated automatically based on the specified return type.
:::note
Make sure to explicitly enable JSON Schema feature when configuring `ChatLanguageModel`,
as it is disabled by default.
:::

The `name` of the generated `JsonSchema` is a simple name of the return type (`getClass().getSimpleName()`),
in this case: "Person".

Once LLM responds, the output is parsed into an object and returned from the AI Service method.
:::note
While we are gradually migrating to Jackson, Gson is still used for parsing the outputs in AI Service,
so Jackson annotations on your POJOs will have no effect.
:::

You can find many examples of supported use cases
[here](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/test/java/dev/langchain4j/service/AiServicesWithJsonSchemaIT.java)
and [here](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/test/java/dev/langchain4j/service/AiServicesWithJsonSchemaWithDescriptionsIT.java).

#### Adding Description

If an LLM does not provide the desired output, classes and fields can be annotated with `@Description`
to give more information and instructions to the LLM.
For example:
```java
@Description("a person")
record Person(@Description("person's name") String name,
              @Description("person's age") int age,
              @Description("person's height") double height,
              @Description("is person married or not") boolean married) {
}
```

#### Limitations
When using JSON Schema with AI Services, there are some limitations:
- It works only with supported OpenAI and Gemini models.
- Support for JSON Schema needs to be enabled explicitly when configuring `ChatLanguageModel`.
- It does not work in the [streaming mode](/tutorials/ai-services#streaming).
- Currently, it works only when return type is a (single) POJO or a `Result<POJO>`.
If you need other types (e.g., `List<POJO>`, `enum`, etc.), please wrap these into a POJO.
We are [working](https://github.com/langchain4j/langchain4j/pull/1938) on supporting more return types soon.
- POJOs can contain:
  - Scalar/simple types (e.g., `String`, `int`/`Integer`, `double`/`Double`, `boolean`/`Boolean`, etc.)
  - `enum`s
  - Nested POJOs
  - `List<T>`, `Set<T>` and `T[]`, where `T` is a scalar, an `enum` or a POJO
- All fields and sub-fields in the generated `JsonSchema` are automatically marked as `required`, there is currently no way to make them optional.
- When LLM does not support JSON Schema feature, or it is not enabled, or return type is not a POJO,
AI Service will fall back to [prompting](/tutorials/structured-outputs#prompting).
- Recursion is currently supported only by OpenAI.


## Tools (Function Calling)

This method assumes (mis)using [tools](/tutorials/tools) to produce structured outputs.
A single tool is specified in the request to the LLM, and the tool parameters describe the desired structure of the output.
If `ToolChoice` is supported by the LLM provider, one can also set `ToolChoice.REQUIRED`
to force the LLM to call this tool.
Once the LLM returns an `AiMessage` with a `ToolExecutionRequest`,
the arguments (which is a valid JSON) are parsed into a POJO.

More info is coming soon.

In the meantime, please read [this section](/tutorials/tools)
and [this article](https://glaforge.dev/posts/2024/11/18/data-extraction-the-many-ways-to-get-llms-to-spit-json-content/).


## Prompting + JSON Mode
More info is coming soon.
In the meantime, please read [this section](/tutorials/ai-services#json-mode)
and [this article](https://glaforge.dev/posts/2024/11/18/data-extraction-the-many-ways-to-get-llms-to-spit-json-content/).


## Prompting

When using prompting, one has to specify the format of the desired output in free-form text
within a system or user message and hope that the LLM will abide. This approach is quite unreliable.
If LLM and LLM provider supports the methods described above, it is better to use those.

More info is coming soon.

In the meantime, please read [this section](/tutorials/ai-services#structured-outputs)
and [this article](https://glaforge.dev/posts/2024/11/18/data-extraction-the-many-ways-to-get-llms-to-spit-json-content/).


## Related Tutorials
- [Data extraction: The many ways to get LLMs to spit JSON content](https://glaforge.dev/posts/2024/11/18/data-extraction-the-many-ways-to-get-llms-to-spit-json-content/) by [Guillaume Laforge](https://glaforge.dev/about/)
