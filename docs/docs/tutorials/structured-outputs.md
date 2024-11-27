---
sidebar_position: 11
---

# Structured Outputs

Many LLMs and LLM providers support generating outputs in a structured format, typically JSON.
These outputs can be easily mapped to Java objects and integrated into other parts of your application.

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
- [Structured Outputs](/tutorials/structured-outputs#structured-outputs-1)
- [Tools (Function Calling)](/tutorials/structured-outputs#tools-function-calling)
- [Prompting + JSON Mode](/tutorials/structured-outputs#prompting--json-mode)
- [Prompting](/tutorials/structured-outputs#prompting)


## Structured Outputs
Some LLM providers (currently only [OpenAI](https://platform.openai.com/docs/guides/structured-outputs) 
and [Google Gemini](https://ai.google.dev/gemini-api/docs/structured-output)) support a specialized 
"Structured Outputs" API that allows specifying JSON schema for the desired output.
You can view all supported LLM providers [here](/integrations/language-models) in the "Structured Outputs" column.

When a JSON schema is specified in the request, the LLM is expected to generate an output that adheres to this schema.
Please note that the JSON schema is specified in a separate attribute in the request to the LLM provider's API
and does not require additional free-form instructions to be included in the prompt (e.g., in system or user messages).

LangChain4j supports the Structured Outputs feature in both the low-level `ChatLanguageModel` API
and the high-level AI Service API.

### Low Level Structured Outputs API

In the low-level `ChatLanguageModel` API, JSON schema can be specified
using `JsonSchema` and `ResponseFormat` when creating a `ChatRequest`:
```java
ChatLanguageModel chatModel = OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4o-mini")
        .responseFormat("json_schema") // see [1] below
        .strictJsonSchema(true) // see [1] below
        .logRequests(true)
        .logResponses(true)
        .build();
// OR
ChatLanguageModel chatModel = GoogleAiGeminiChatModel.builder()
        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
        .modelName("gemini-1.5-flash")
        .responseFormat(ResponseFormat.JSON) // see [2] below
        .temperature(0.0)
        .logRequestsAndResponses(true)
        .build();

UserMessage userMessage = UserMessage.from("""
        John is 42 years old and lives an independent life.
        He stands 1.75 meters tall and carries himself with confidence.
        Currently unmarried, he enjoys the freedom to focus on his personal goals and interests.
        """);

ResponseFormat responseFormat = ResponseFormat.builder()
        .type(JSON) // see [3] below
        .jsonSchema(JsonSchema.builder()
                .name("Person") // see [4] below
                .rootElement(JsonObjectSchema.builder() // see [5] below
                        .addStringProperty("name")
                        .addIntegerProperty("age")
                        .addNumberProperty("height")
                        .addBooleanProperty("married")
                        .required("name", "age", "height", "married")
                        .build())
                 .build())
        .build();

ChatRequest chatRequest = ChatRequest.builder()
        .messages(userMessage)
        .responseFormat(responseFormat)
        .build();

ChatResponse chatResponse = chatModel.chat(chatRequest);

String output = chatResponse.aiMessage().text();
System.out.println(output); // {"name":"John","age":42,"height":1.75,"married":false}

Person person = new ObjectMapper().readValue(output, Person.class);
System.out.println(person); // Person[name=John, age=42, height=1.75, married=false]
```
Notes:
- [1] - This is required to enable the Structured Outputs feature for OpenAI, see more details [here](/integrations/language-models/open-ai#structured-outputs-for-response-format).
- [2] - This is required to enable the Structured Outputs feature for [Google AI Gemini](/integrations/language-models/google-ai-gemini).
- [3] - Response format type can be either `TEXT` (default) or `JSON`.
- [4] - OpenAI requires specifying the name for the schema.
- [5] - In most cases, the root element must be of `JsonObjectSchema` type,
however Gemini allows `JsonEnumSchema` and `JsonArraySchema` as well.

:::note
Make sure to explicitly enable Structured Outputs feature when configuring `ChatLanguageModel`,
as it is disabled by default.
:::

To support LLM-provider-agnostic way for defining JSON schema,
LangChain4j offers `ResponseFormat` and `JsonSchema` types.

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

### High Level Structured Outputs API

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
- [2] - This is required to enable the Structured Outputs feature for OpenAI, see more details [here](/integrations/language-models/open-ai#structured-outputs-for-response-format).
- [3] - This is required to enable the Structured Outputs feature for [Google AI Gemini](/integrations/language-models/google-ai-gemini).

When AI Service returns a POJO **and** used `ChatLanguageModel` supports Structured Outputs **and** Structured Outputs are enabled,
`JsonSchema`/`ResponseFormat` will be generated automatically from the specified return type.
:::note
Make sure to explicitly enable Structured Outputs feature when configuring `ChatLanguageModel`,
as it is disabled by default.
:::
:::note
The `name` of the generated `JsonSchema` is a simple name of the return type, in this case: "Person".
:::

Once LLM responds, the output is parsed into an object and returned to the caller.
:::note
While we are gradually migrating to Jackson, Gson is still used for parsing the outputs,
so Jackson annotations on your POJOs will have no effect.
:::

You can find many examples of supported use cases
[here](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/test/java/dev/langchain4j/service/AiServicesWithJsonSchemaIT.java)
and [here](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/test/java/dev/langchain4j/service/AiServicesWithJsonSchemaWithDescriptionsIT.java).

### Limitations
When using Structured Outputs with AI Services, there are some limitations:
- It works only with supported OpenAI and Gemini models.
- Support for Structured Outputs needs to be enabled explicitly when configuring `ChatLanguageModel`.
- It does not work in the [streaming mode](/tutorials/ai-services#streaming).
- Currently, it works only when return type is a (single) POJO or a `Result<POJO>`.
If you need other types (e.g., `List<POJO>`, `enum`, etc.), please wrap these into a POJO.
We are [working](https://github.com/langchain4j/langchain4j/pull/1938) on supporting more return types soon.
- POJOs can contain:
  - Scalar/simple types (e.g., `String`, `int`/`Integer`, `double`/`Double`, `boolean`/`Boolean`, etc.)
  - `enum`s
  - Nested POJOs
  - `List<T>`, `Set<T>` and `T[]`, where `T` is a scalar, an enum or a POJO
- All fields and sub-fields in the generated `JsonSchema` are marked as `required`, there is currently no way to make them optional.
- Classes and fields can be (optionally) annotated with `@Description` to provide more information to the LLM.
For example:
```java
@Description("a person")
record Person(@Description("person's name") String name,
              @Description("person's age") int age,
              @Description("person's height") double height,
              @Description("is person married or not") boolean married) {
}
```
- When LLM does not support Structured Outputs feature, or it is not enabled, or return type is not a POJO,
AI Service will fall back to [prompting](/tutorials/structured-outputs#prompting).
- Recursion is currently supported only by OpenAI.


## Tools (Function Calling)
More info is coming soon.
In the meantime, please read [this section](/tutorials/tools)
and [this article](https://glaforge.dev/posts/2024/11/18/data-extraction-the-many-ways-to-get-llms-to-spit-json-content/).


## Prompting + JSON Mode
More info is coming soon.
In the meantime, please read [this section](/tutorials/ai-services#json-mode)
and [this article](https://glaforge.dev/posts/2024/11/18/data-extraction-the-many-ways-to-get-llms-to-spit-json-content/).


## Prompting
More info is coming soon.
In the meantime, please read [this section](/tutorials/ai-services#structured-outputs)
and [this article](https://glaforge.dev/posts/2024/11/18/data-extraction-the-many-ways-to-get-llms-to-spit-json-content/).


## Related Tutorials
- [Data extraction: The many ways to get LLMs to spit JSON content](https://glaforge.dev/posts/2024/11/18/data-extraction-the-many-ways-to-get-llms-to-spit-json-content/) by [Guillaume Laforge](https://glaforge.dev/about/)
