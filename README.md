# LangChain for Java: Supercharge your Java application with the power of LLMs

[![](https://img.shields.io/twitter/follow/langchain4j)](https://twitter.com/intent/follow?screen_name=langchain4j)
[![](https://dcbadge.vercel.app/api/server/JzTFvyjG6R?compact=true&style=flat)](https://discord.gg/JzTFvyjG6R)

## Introduction

Welcome!

The goal of LangChain4j is to simplify integrating AI/LLM capabilities into Java applications.

Here's how:
1. **Unified APIs:**
   LLM providers (like OpenAI or Google Vertex AI) and embedding (vector) stores (such as Pinecone or Vespa)
   use proprietary APIs. LangChain4j offers a unified API to avoid the need for learning and implementing specific APIs for each of them.
   To experiment with a different LLM or embedding store, you can easily switch between them without the need to rewrite your code.
   LangChain4j currently supports over 10 popular LLM providers and more than 15 embedding stores.
   Think of it as a Hibernate, but for LLMs and embedding stores.
2. **Comprehensive Toolbox:**
   During the past year, the community has been building numerous LLM-powered applications,
   identifying common patterns, abstractions, and techniques. LangChain4j has refined these into practical code.
   Our toolbox includes tools ranging from low-level prompt templating, memory management, and output parsing
   to high-level patterns like Agents and RAGs.
   For each pattern and abstraction, we provide an interface along with multiple ready-to-use implementations based on proven techniques.
   Whether you're building a chatbot or developing a RAG with a complete pipeline from data ingestion to retrieval,
   LangChain4j offers a wide variety of options.
3. **Numerous Examples:**
   These [examples](https://github.com/langchain4j/langchain4j-examples) showcase how to begin creating various LLM-powered applications,
   providing inspiration and enabling you to start building quickly.

LangChain4j began development in early 2023 amid the ChatGPT hype.
We noticed a lack of Java counterparts to the numerous Python and JavaScript LLM libraries and frameworks,
and we had to fix that!
Although "LangChain" is in our name, the project is a fusion of ideas and concepts from LangChain, Haystack,
LlamaIndex, and the broader community, spiced up with a touch of our own innovation.

We actively monitor community developments, aiming to quickly incorporate new techniques and integrations,
ensuring you stay up-to-date.
The library is under active development. While some features from the Python version of LangChain
are still being worked on, the core functionality is in place, allowing you to start building LLM-powered apps now!

For easier integration, LangChain4j also includes integration with
Quarkus ([extension](https://quarkus.io/extensions/io.quarkiverse.langchain4j/quarkus-langchain4j-core))
and Spring Boot ([starters](https://github.com/langchain4j/langchain4j-spring)).

## Code Examples

Please see examples of how LangChain4j can be used in [langchain4j-examples](https://github.com/langchain4j/langchain4j-examples) repo:

- [Examples in plain Java](https://github.com/langchain4j/langchain4j-examples/tree/main/other-examples/src/main/java)
- [Examples with Quarkus](https://github.com/quarkiverse/quarkus-langchain4j/tree/main/samples) (uses [quarkus-langchain4j](https://github.com/quarkiverse/quarkus-langchain4j) dependency)
- [Example with Spring Boot](https://github.com/langchain4j/langchain4j-examples/blob/main/spring-boot-example/src/test/java/dev/example/CustomerSupportApplicationTest.java)

## Documentation
Documentation can be found [here](https://docs.langchain4j.dev).

## Tutorials
Tutorials can be found [here](https://docs.langchain4j.dev/tutorials).

## Useful Materials
[Useful Materials](https://docs.langchain4j.dev/useful-materials)

## Library Structure
LangChain4j features a modular design, comprising:
- The `langchain4j-core` module, which defines core abstractions (such as `ChatLanguageModel` and `EmbeddingStore`) and their APIs.
- The main `langchain4j` module, containing useful tools like `ChatMemory`, `OutputParser` as well as a high-level features like `AiServices`.
- A wide array of `langchain4j-{integration}` modules, each providing integration with various LLM providers and embedding stores into LangChain4j.
  You can use the `langchain4j-{integration}` modules independently. For additional features, simply import the main `langchain4j` dependency.

## News

30 January:
 - Support for [Advanced RAG](https://github.com/langchain4j/langchain4j/pull/538).
Examples can be found [here](https://github.com/langchain4j/langchain4j-examples/tree/main/rag-examples/src/main/java).
 - Support for image inputs.
Currently supported by [OpenAI](https://github.com/langchain4j/langchain4j-examples/blob/main/open-ai-examples/src/main/java/OpenAiChatModelExamples.java), Vertex AI Gemini, Ollama and Qwen.
 - [Mistral AI](https://mistral.ai/) [integration](https://github.com/langchain4j/langchain4j-examples/blob/main/mistral-ai-examples/src/main/java/MistralAiChatModelExamples.java) by [@czelabueno](https://github.com/czelabueno)
 - [Azure AI Search](https://azure.microsoft.com/en-us/products/ai-services/ai-search) integration by [@jdubois](https://github.com/jdubois)
 - [Qdrant](https://qdrant.tech/) integration by [@Anush008](https://github.com/Anush008)
- [And much more](https://github.com/langchain4j/langchain4j/releases/tag/0.26.1)

<details>
  <summary>Previous News</summary>

22 December:
- Azure OpenAI:
  - Using official Azure SDK by [@jdubois](https://github.com/jdubois)
  - Image generation with DALL·E by [@jdubois](https://github.com/jdubois)
- OpenAI:
  - [Image generation with DALL·E](https://github.com/langchain4j/langchain4j-examples/blob/main/open-ai-examples/src/main/java/OpenAiImageModelExamples.java) by [@Heezer](https://github.com/Heezer)
  - Parallel function calling, json output, precise token estimation by [@langchain4j](https://github.com/langchain4j)
- [Integration with Google Gemini](https://github.com/langchain4j/langchain4j-examples/blob/main/vertex-ai-gemini-examples/src/main/java/VertexAiGeminiChatModelExamples.java) by [@kuraleta](https://github.com/kuraleta)
- Ollama:
  - [Chat API](https://github.com/langchain4j/langchain4j-examples/tree/main/ollama-examples) by [@fintanmm](https://github.com/fintanmm)
  - Json output and more parameters by [@langchain4j](https://github.com/langchain4j)
- [Integration with Neo4j](https://github.com/langchain4j/langchain4j-examples/blob/main/neo4j-example/src/main/java/Neo4jEmbeddingStoreExample.java) by [@vga91](https://github.com/vga91)
- Integration with ChatGLM by [@Martin7-1](https://github.com/Martin7-1)
- [And more](https://github.com/langchain4j/langchain4j/releases/tag/0.25.0)

12 November:
- Integration with [OpenSearch](https://opensearch.org/) by [@riferrei](https://github.com/riferrei)
- Add support for loading documents from S3 by [@jmgang](https://github.com/jmgang)
- Integration with [PGVector](https://github.com/pgvector/pgvector) by [@kevin-wu-os](https://github.com/kevin-wu-os)
- Integration with [Ollama](https://ollama.ai/) by  [@Martin7-1](https://github.com/Martin7-1)
- Integration with [Amazon Bedrock](https://aws.amazon.com/bedrock/) by [@pascalconfluent](https://github.com/pascalconfluent)
- Adding Memory Id to Tool Method Call by [@benedictstrube](https://github.com/benedictstrube)
- [And more](https://github.com/langchain4j/langchain4j/releases/tag/0.24.0)

29 September:
- Updates to models API: return `Response<T>` instead of `T`. `Response<T>` contains token usage and finish reason.
- All model and embedding store integrations now live in their own modules
- Integration with [Vespa](https://vespa.ai/) by [@Heezer](https://github.com/Heezer)
- Integration with [Elasticsearch](https://www.elastic.co/) by [@Martin7-1](https://github.com/Martin7-1)
- Integration with [Redis](https://redis.io/) by [@Martin7-1](https://github.com/Martin7-1)
- Integration with [Milvus](https://milvus.io/) by [@IuriiKoval](https://github.com/IuriiKoval)
- Integration with [Astra DB](https://www.datastax.com/products/datastax-astra) and [Cassandra](https://cassandra.apache.org/) by [@clun](https://github.com/clun)
- Added support for overlap in document splitters
- Some bugfixes and smaller improvements 

29 August:
- Offline [text classification with embeddings](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/embedding/classification/EmbeddingModelTextClassifierExample.java)
- Integration with [Google Vertex AI](https://cloud.google.com/vertex-ai) by [@kuraleta](https://github.com/kuraleta)
- Reworked [document splitters](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/main/java/dev/langchain4j/data/document/splitter/DocumentSplitters.java)
- In-memory embedding store can now be easily persisted
- [And more](https://github.com/langchain4j/langchain4j/releases/tag/0.22.0)

19 August:
- Integration with [Azure OpenAI](https://learn.microsoft.com/en-us/azure/ai-services/openai/overview) by [@kuraleta](https://github.com/kuraleta)
- Integration with Qwen models (DashScope) by [@jiangsier-xyz](https://github.com/jiangsier-xyz)
- [Integration with Chroma](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/embedding/store/ChromaEmbeddingStoreExample.java) by [@kuraleta](https://github.com/kuraleta)
- [Support for persistent ChatMemory](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithPersistentMemoryForEachUserExample.java)

10 August:
- [Integration with Weaviate](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/embedding/store/WeaviateEmbeddingStoreExample.java) by [@Heezer](https://github.com/Heezer)
- [Support for DOC, XLS and PPT document types](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/DocumentLoaderExamples.java) by [@oognuyh](https://github.com/oognuyh)
- [Separate chat memory for each user](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithMemoryForEachUserExample.java)
- [Custom in-process embedding models](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/embedding/model/InProcessEmbeddingModelExamples.java)
- Added lots of Javadoc
- [And more](https://github.com/langchain4j/langchain4j/releases/tag/0.19.0)

26 July:
- We've added integration with [LocalAI](https://localai.io/). Now, you can use LLMs hosted locally!
- Added support for [response streaming in AI Services](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithStreamingExample.java).

21 July:
- Now, you can do [text embedding inside your JVM](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/embedding/model/InProcessEmbeddingModelExamples.java).

17 July:
- You can now try out OpenAI's `gpt-3.5-turbo` and `text-embedding-ada-002` models with LangChain4j for free, without needing an OpenAI account and keys! Simply use the API key "demo".

15 July:
- Added EmbeddingStoreIngestor
- Redesigned document loaders (see FileSystemDocumentLoader)
- Simplified ConversationalRetrievalChain
- Renamed DocumentSegment into TextSegment
- Added output parsers for numeric types
- Added @UserName for AI Services
- Fixed [23](https://github.com/langchain4j/langchain4j/issues/23) and [24](https://github.com/langchain4j/langchain4j/issues/24)

11 July:

- Added ["Dynamic Tools"](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithDynamicToolsExample.java):
  Now, the LLM can generate code for tasks that require precise calculations, such as math and string manipulation. This will be dynamically executed in a style akin to GPT-4's code interpreter!
  We use [Judge0, hosted by Rapid API](https://rapidapi.com/judge0-official/api/judge0-ce/pricing), for code execution. You can subscribe and receive 50 free executions per day.

5 July:

- Now you can [add your custom knowledge base to "AI Services"](https://github.com/langchain4j/langchain4j-examples/blob/main/spring-boot-example/src/test/java/dev/example/CustomerSupportApplicationTest.java).
  Relevant information will be automatically retrieved and injected into the prompt. This way, the LLM will have a
  context of your data and will answer based on it!
- The current date and time can now be automatically injected into the prompt using
  special `{{current_date}}`, `{{current_time}}` and `{{current_date_time}}` placeholders.

3 July:

- Added support for Spring Boot 3

2 July:

- [Added Spring Boot Starter](https://github.com/langchain4j/langchain4j-examples/blob/main/spring-boot-example/src/test/java/dev/example/CustomerSupportApplicationTest.java)
- Added support for Hugging Face models

1 July:

- [Added "Tools"](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithToolsExample.java) (support for OpenAI functions)
</details>

## Highlights

You can define declarative "AI Services" that are powered by LLMs:

```java
interface Assistant {

    String chat(String userMessage);
}

Assistant assistant = AiServices.create(Assistant.class, model);

String answer = assistant.chat("Hello");
    
System.out.println(answer);
// Hello! How can I assist you today?
```

You can use LLM as a classifier:

```java
enum Sentiment {
    POSITIVE, NEUTRAL, NEGATIVE
}

interface SentimentAnalyzer {

    @UserMessage("Analyze sentiment of {{it}}")
    Sentiment analyzeSentimentOf(String text);

    @UserMessage("Does {{it}} have a positive sentiment?")
    boolean isPositive(String text);
}

SentimentAnalyzer sentimentAnalyzer = AiServices.create(SentimentAnalyzer.class, model);

Sentiment sentiment = sentimentAnalyzer.analyzeSentimentOf("It is good!");
// POSITIVE

boolean positive = sentimentAnalyzer.isPositive("It is bad!");
// false
```

You can easily extract structured information from unstructured data:

```java
class Person {

    private String firstName;
    private String lastName;
    private LocalDate birthDate;
}

interface PersonExtractor {

    @UserMessage("Extract information about a person from {{text}}")
    Person extractPersonFrom(@V("text") String text);
}

PersonExtractor extractor = AiServices.create(PersonExtractor.class, model);

String text = "In 1968, amidst the fading echoes of Independence Day, "
    + "a child named John arrived under the calm evening sky. "
    + "This newborn, bearing the surname Doe, marked the start of a new journey.";

Person person = extractor.extractPersonFrom(text);
// Person { firstName = "John", lastName = "Doe", birthDate = 1968-07-04 }
```

You can provide tools that LLMs can use! It can be anything: retrieve information from DB, call APIs, etc.
See example [here](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithToolsExample.java).

## Compatibility

- Java: 8 or higher
- Spring Boot: 2 or 3

## Getting started

1. Add LangChain4j OpenAI dependency to your project:
    - Maven:
      ```xml
      <dependency>
          <groupId>dev.langchain4j</groupId>
          <artifactId>langchain4j-open-ai</artifactId>
          <version>0.28.0</version>
      </dependency>
      ```
    - Gradle:
      ```groovy
      implementation 'dev.langchain4j:langchain4j-open-ai:0.28.0'
      ```

2. Import your OpenAI API key:
    ```java
    String apiKey = System.getenv("OPENAI_API_KEY");
    ```
    You can also use the API key `demo` to test OpenAI, which we provide for free.
    [How to get an API key?](https://github.com/langchain4j/langchain4j#how-to-get-an-api-key)


3. Create an instance of a model and start interacting:
    ```java
    OpenAiChatModel model = OpenAiChatModel.withApiKey(apiKey);
    
    String answer = model.generate("Hello world!");
    
    System.out.println(answer); // Hello! How can I assist you today?
    ```
## Supported LLM Integrations ([Docs](https://docs.langchain4j.dev/category/integrations))
| Provider                                                                                           | Native Image     | [Sync Completion](https://docs.langchain4j.dev/category/language-models) | [Streaming Completion](https://docs.langchain4j.dev/integrations/language-models/response-streaming) | [Embedding](https://docs.langchain4j.dev/category/embedding-models) | [Image Generation](https://docs.langchain4j.dev/category/image-models) | [Scoring](https://docs.langchain4j.dev/category/scoring-models) | [Function Calling](https://docs.langchain4j.dev/tutorials/tools)
|----------------------------------------------------------------------------------------------------| ------------- |---------------------------------------------------------------------| ----------- | ------ |-------------------------------| ------ |--------------------------------------------------------------------------------------------|
| [OpenAI](https://docs.langchain4j.dev/integrations/language-models/openai)                         |  | ✅ | ✅ | ✅ | ✅ | | ✅ |                                                                                                
| [Azure OpenAI](https://docs.langchain4j.dev/integrations/language-models/azure-openai)             |  | ✅ | ✅ | ✅ | ✅ | | ✅ | 
| [Hugging Face](https://docs.langchain4j.dev/integrations/language-models/huggingface)              |  | ✅ | | ✅ |  | | |  |
| [Amazon Bedrock](https://docs.langchain4j.dev/integrations/language-models/amazon-bedrock)         |  | ✅  | |✅ | ✅ | | |
| [Google Vertex AI Gemini](https://docs.langchain4j.dev/integrations/language-models/google-gemini) |  | ✅ | ✅ | | ✅ | | ✅ |
| [Google Vertex AI](https://docs.langchain4j.dev/integrations/language-models/google-palm)          | ✅ | ✅ | | ✅ | ✅ | | |
| [Mistral AI](https://docs.langchain4j.dev/integrations/language-models/mistralai)                  |  | ✅ | ✅ | ✅ |  | |✅ |
| [DashScope](https://docs.langchain4j.dev/integrations/language-models/dashscope)                   |  | ✅ | ✅ |✅ |  | | |
| [LocalAI](https://docs.langchain4j.dev/integrations/language-models/localai)                       |  | ✅ | ✅ | ✅ |  | | ✅ |
| [Ollama](https://docs.langchain4j.dev/integrations/language-models/ollama)                         |  | ✅ | ✅ | ✅ |  | | |
| [Cohere](https://docs.langchain4j.dev/integrations/reranking-models/cohere)                        |  | | | |  | ✅| |
| [Qianfan](https://docs.langchain4j.dev/integrations/language-models/qianfan)                       |  | ✅ | ✅ | ✅ |  | |✅ |
| [ChatGLM](https://docs.langchain4j.dev/integrations/language-models/chatglm)                       |  | ✅ | | |  | |
| [Nomic](https://docs.langchain4j.dev/integrations/language-models/nomic)                           |  | | |✅ |  | | |
| [Anthropic](https://docs.langchain4j.dev/integrations/language-models/anthropic)                   |  |✅ | | |  | | |
| [Zhipu AI](https://docs.langchain4j.dev/integrations/language-models/zhipuai)                      |  |✅| ✅| ✅|  | |✅ |

## Disclaimer

Please note that the library is in active development and:

- Some features are still missing. We are working hard on implementing them ASAP.
- API might change at any moment. At this point, we prioritize good design in the future over backward compatibility
  now. We hope for your understanding.
- We need your input! Please [let us know](https://github.com/langchain4j/langchain4j/issues/new/choose) what features you need and your concerns about the current implementation.

## Current features (this list is outdated, we have much more):

- AI Services:
    - [Simple](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/SimpleServiceExample.java)
    - [With Memory](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithMemoryExample.java)
    - [With Tools](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithToolsExample.java)
    - [With Streaming](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithStreamingExample.java)
    - [With RAG](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithRetrieverExample.java)
    - [With Auto-Moderation](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithAutoModerationExample.java)
    - [With Structured Outputs, Structured Prompts, etc](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/OtherServiceExamples.java)
- Integration with [OpenAI](https://platform.openai.com/docs/introduction) and [Azure OpenAI](https://learn.microsoft.com/en-us/azure/ai-services/openai/overview) for:
    - [Chats](https://platform.openai.com/docs/guides/chat) (sync + streaming + functions)
    - [Completions](https://platform.openai.com/docs/guides/completion) (sync + streaming)
    - [Embeddings](https://platform.openai.com/docs/guides/embeddings)
- Integration with [Google Vertex AI](https://cloud.google.com/vertex-ai) for:
    - [Chats](https://cloud.google.com/vertex-ai/docs/generative-ai/chat/chat-prompts)
    - [Completions](https://cloud.google.com/vertex-ai/docs/generative-ai/text/text-overview)
    - [Embeddings](https://cloud.google.com/vertex-ai/docs/generative-ai/embeddings/get-text-embeddings)
- Integration with [Hugging Face Inference API](https://huggingface.co/docs/api-inference/index) for:
    - [Chats](https://huggingface.co/docs/api-inference/detailed_parameters#text-generation-task)
    - [Completions](https://huggingface.co/docs/api-inference/detailed_parameters#text-generation-task)
    - [Embeddings](https://huggingface.co/docs/api-inference/detailed_parameters#feature-extraction-task)
- Integration with [LocalAI](https://localai.io/) for:
  - Chats (sync + streaming + functions)
  - Completions (sync + streaming)
  - Embeddings
- Integration with [DashScope](https://dashscope.aliyun.com/) for:
    - Chats (sync + streaming)
    - Completions (sync + streaming)
    - Embeddings
- [Chat memory](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ChatMemoryExamples.java)
- [Persistent chat memory](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithPersistentMemoryForEachUserExample.java)
- [Chat with Documents](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ChatWithDocumentsExamples.java)
- Integration with [Astra DB](https://www.datastax.com/products/datastax-astra) and [Cassandra](https://cassandra.apache.org/)
- [Integration](https://github.com/langchain4j/langchain4j-examples/blob/main/chroma-example/src/main/java/ChromaEmbeddingStoreExample.java) with [Chroma](https://www.trychroma.com/)
- [Integration](https://github.com/langchain4j/langchain4j-examples/blob/main/elasticsearch-example/src/main/java/ElasticsearchEmbeddingStoreExample.java) with [Elasticsearch](https://www.elastic.co/)
- [Integration](https://github.com/langchain4j/langchain4j-examples/blob/main/milvus-example/src/main/java/MilvusEmbeddingStoreExample.java) with [Milvus](https://milvus.io/)
- [Integration](https://github.com/langchain4j/langchain4j-examples/blob/main/pinecone-example/src/main/java/PineconeEmbeddingStoreExample.java) with [Pinecone](https://www.pinecone.io/)
- [Integration](https://github.com/langchain4j/langchain4j-examples/blob/main/redis-example/src/main/java/RedisEmbeddingStoreExample.java) with [Redis](https://redis.io/)
- [Integration](https://github.com/langchain4j/langchain4j-examples/blob/main/vespa-example/src/main/java/VespaEmbeddingStoreExample.java) with [Vespa](https://vespa.ai/)
- [Integration](https://github.com/langchain4j/langchain4j-examples/blob/main/weaviate-example/src/main/java/WeaviateEmbeddingStoreExample.java) with [Weaviate](https://weaviate.io/)
- [In-memory embedding store](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/embedding/store/InMemoryEmbeddingStoreExample.java) (can be persisted)
- [Structured outputs](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/OtherServiceExamples.java)
- [Prompt templates](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/PromptTemplateExamples.java)
- [Structured prompt templates](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/StructuredPromptTemplateExamples.java)
- [Streaming of LLM responses](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/StreamingExamples.java)
- [Loading txt, html, pdf, doc, xls and ppt documents from the file system and via URL](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/DocumentLoaderExamples.java)
- [Splitting documents into segments](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ChatWithDocumentsExamples.java):
    - by paragraphs, lines, sentences, words, etc
    - recursively
    - with overlap
- Token count estimation (so that you can predict how much you will pay)

## Coming soon:

- Extending "AI Service" features
- Integration with more LLM providers (commercial and free)
- Integrations with more embedding stores (commercial and free)
- Support for more document types
- Long-term memory for chatbots and agents
- Chain-of-Thought and Tree-of-Thought

## Request features

Please [let us know](https://github.com/langchain4j/langchain4j/issues/new/choose) what features you need!

## Contribute

Please help us make this open-source library better by contributing.

Some guidelines:
1. Follow [Google's Best Practices for Java Libraries](https://jlbp.dev/).
2. Keep the code compatible with Java 8.
3. Avoid adding new dependencies as much as possible. If absolutely necessary, try to (re)use the same libraries which are already present.
4. Follow existing code styles present in the project.
5. Ensure to add Javadoc where necessary.
6. Provide unit and/or integration tests for your code.
7. Large features should be discussed with maintainers before implementation.

## Use cases

You might ask why would I need all of this?
Here are a couple of examples:

- You want to implement a custom AI-powered chatbot that has access to your data and behaves the way you want it:
    - Customer support chatbot that can:
        - politely answer customer questions
        - take /change/cancel orders
    - Educational assistant that can:
        - Teach various subjects
        - Explain unclear parts
        - Assess user's understanding/knowledge
- You want to process a lot of unstructured data (files, web pages, etc) and extract structured information from them.
  For example:
    - extract insights from customer reviews and support chat history
    - extract interesting information from the websites of your competitors
    - extract insights from CVs of job applicants
- You want to generate information, for example:
    - Emails tailored for each of your customers
    - Content for your app/website:
        - Blog posts
        - Stories
- You want to transform information, for example:
    - Summarize
    - Proofread and rewrite
    - Translate

## Best practices

We highly recommend
watching [this amazing 90-minute tutorial](https://www.deeplearning.ai/short-courses/chatgpt-prompt-engineering-for-developers/)
on prompt engineering best practices, presented by Andrew Ng (DeepLearning.AI) and Isa Fulford (OpenAI).
This course will teach you how to use LLMs efficiently and achieve the best possible results. Good investment of your
time!

Here are some best practices for using LLMs:

- Be responsible. Use AI for Good.
- Be specific. The more specific your query, the best results you will get.
- Add a ["Let’s think step by step" instruction](https://arxiv.org/pdf/2205.11916.pdf) to your prompt.
- Specify steps to achieve the desired goal yourself. This will make the LLM do what you want it to do.
- Provide examples. Sometimes it is best to show LLM a few examples of what you want instead of trying to explain it.
- Ask LLM to provide structured output (JSON, XML, etc). This way you can parse response more easily and distinguish
  different parts of it.
- Use unusual delimiters, such as \```triple backticks``` to help the LLM distinguish
  data or input from instructions.

## How to get an API key
You will need an API key from OpenAI (paid) or Hugging Face (free) to use LLMs hosted by them.

We recommend using OpenAI LLMs (`gpt-3.5-turbo` and `gpt-4`) as they are by far the most capable and are reasonably priced.

It will cost approximately $0.01 to generate 10 pages (A4 format) of text with `gpt-3.5-turbo`. With `gpt-4`, the cost will be $0.30 to generate the same amount of text. However, for some use cases, this higher cost may be justified.

[How to get OpenAI API key](https://www.howtogeek.com/885918/how-to-get-an-openai-api-key/).

For embeddings, we recommend using one of the models from the [Hugging Face MTEB leaderboard](https://huggingface.co/spaces/mteb/leaderboard).
You'll have to find the best one for your specific use case.

Here's how to get a Hugging Face API key:
- Create an account on https://huggingface.co
- Go to https://huggingface.co/settings/tokens
- Generate a new access token
