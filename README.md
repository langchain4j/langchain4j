# LangChain4j: Supercharge your Java application with the power of AI

Do you want to integrate various AI tools and services into your Java application?

Did you hear about [LangChain](https://github.com/hwchase17/langchain) and want to try it in Java?

This library might be what you need.

## Highlights
You can declaratively define concise "AI Services" that are powered by LLMs:
```
interface Comedian {

    String tellMeJoke();
    String tellMeJokeAbout(String topic);
    Funniness evaluate(String joke);
}

enum Funniness {
    VERY_FUNNY, FUNNY, MEH, NOT_FUNNY
}


Comedian comedian = AiServices.create(Comedian.class, model);

String joke = comedian.tellMeJoke();
// Why couldn't the bicycle stand up by itself? Because it was two-tired!

String anotherJoke = comedian.tellMeJokeAbout("tomato");
// Why did the tomato turn red? Because it saw the salad dressing!

Funniness funniness = comedian.evaluate(joke);
// FUNNY
```

You can easily extract structured information from unstructured data:
```
class Person {

    private String firstName;
    private String lastName;
    private LocalDate birthDate;

    public String toString() {
        ...
    }
}

interface PersonExtractor {

    Person extractPersonFrom(String text);
}


PersonExtractor extractor = AiServices.create(PersonExtractor.class, model);

String text = "In 1968, amidst the fading echoes of Independence Day, "
    + "a child named John arrived under the calm evening sky. "
    + "This newborn, bearing the surname Doe, marked the start of a new journey.";
    
Person person = extractor.extractPersonFrom(text);
// Person { firstName = "John", lastName = "Doe", birthDate = 1968-07-04 }
```

You can have more control over LLM:
```
interface Translator {

    @SystemMessage("You are a professional translator into {{language}}")
    @UserMessage("Translate the following text: {{text}}")
    String translate(@V("text") String text, @V("language") String language);
}


Translator translator = AiServices.create(Translator.class, model);

String translation = translator.translate("Hello, how are you?", "Italian");
// Ciao, come stai?
```

## Project goals
The goal of this project is to simplify the integration of AI capabilities into your Java application. This can be
achieved thanks to:

- **Simple and coherent layer of abstractions**, so that your code does not depend on concrete implementations (LLM
  providers, embedding store providers, etc) and you can swap them easily
- **Numerous implementations of the above-mentioned interfaces**, so that you have freedom to choose
- **Range of in-demand features on top of LLMs, such as:**
    - **Prompt templates**, so that you can achieve the best possible quality of LLM responses
    - **Memory**, so that LLM has a context of your current and previous conversations
    - **Ingesting your own data** (documentation, codebase, etc.), so that LLM can act and answer based on your data
    - **Chains**, so that you don't need to write lots of boilerplate code for common use-cases
    - **Structured outputs**, so that you can receive responses from LLM as Java objects
    - **Autonomous agents**, so that you can delegate tasks (defined on the fly) to LLM, and it will do its best to
      complete them
    - **"AI Services"**, so that you can declaratively define complex AI behavior behind a simple API
    - **Auto-moderation**, so that you can be sure that all inputs and outputs to/from LLM are not harmful

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
  - extract insights from customer reviews
  - extract insights from customer support chat history
  - extract insights from the websites of your competitors
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

## Disclaimer
Please note that the library is in active development and:
- Many features are still missing. We are working hard on implementing them ASAP.
- API might change at any moment. At this point, we prioritize good design in the future over backward compatibility now. We hope for your understanding.
- We need your input! Please let us know what features you need and your concerns about the current implementation.

## Current capabilities:
- [AI Services](https://github.com/langchain4j/langchain4j-examples/blob/main/src/main/java/AiServicesExamples.java)
- Integration with [OpenAI (ChatGPT)](https://platform.openai.com/docs/introduction) for:
    - [Chats](https://platform.openai.com/docs/guides/chat) (sync + streaming)
    - [Completions](https://platform.openai.com/docs/guides/completion) (sync + streaming)
    - [Embeddings](https://platform.openai.com/docs/guides/embeddings)
- [Memory for Chats](https://github.com/langchain4j/langchain4j-examples/blob/main/src/main/java/ChatMemoryExamples.java)
- [Chat with Documents](https://github.com/langchain4j/langchain4j-examples/blob/main/src/main/java/ChatWithDocumentsExamples.java)
- Integration with [Pinecone](https://docs.pinecone.io/docs/overview) embedding store
- [In-memory embedding store](https://github.com/ai-for-java/embeddings4j) (for prototyping and testing)
- [Structured outputs](https://github.com/langchain4j/langchain4j-examples/blob/main/src/main/java/AiServicesExamples.java)
- [Prompt templates](https://github.com/langchain4j/langchain4j-examples/blob/main/src/main/java/PromptTemplateExamples.java)
- [Structured prompt templates](https://github.com/langchain4j/langchain4j-examples/blob/main/src/main/java/StructuredPromptTemplateExamples.java)
- [Streaming of LLM responses](https://github.com/langchain4j/langchain4j-examples/blob/main/src/main/java/StreamingExamples.java)
- [Loading text and PDF documents from the file system and via URL](https://github.com/langchain4j/langchain4j-examples/blob/main/src/main/java/ChatWithDocumentsExamples.java)
- [Splitting documents into segments](https://github.com/langchain4j/langchain4j-examples/blob/main/src/main/java/ChatWithDocumentsExamples.java):
  - by paragraph
  - by sentence
  - by character count
- Token count estimation (so that you can predict how much you will pay)

## Coming soon:
- [OpenAI's Functions](https://openai.com/blog/function-calling-and-other-api-updates)
- Extending "AI Service" features
- Integration with more LLM providers (commercial and open)
- Integrations with more embedding stores (commercial and open)
- Support for more document types
- Automatic moderation of inputs and outputs to/from LLM
- Autonomous agents that can use tools:
    - Searching the internet for up-to-date information
    - Sending E-mails and messages
    - Calling external APIs
    - Calling internal functions
    - etc
- Long-term memory for chatbots and agents

**Please [let us know what features you need](https://github.com/langchain4j/langchain4j/issues/new)!**

## Start using

Maven:

```
<dependency>
  <groupId>dev.langchain4j</groupId>
  <artifactId>langchain4j</artifactId>
  <version>0.5.0</version>
</dependency>
```

Gradle:

```
implementation 'dev.langchain4j:langchain4j:0.5.0'
```

## Request features

Please [let us know what features you need](https://github.com/ai-for-java/ai4j/issues/new).

## Contribute

Please help us make this open-source library better by contributing.

## Best practices

We highly recommend
watching [this amazing 90-minute tutorial](https://www.deeplearning.ai/short-courses/chatgpt-prompt-engineering-for-developers/)
on prompt engineering best practices, presented by Andrew Ng (DeepLearning.AI) and Isa Fulford (OpenAI).
This course will teach you how to use LLMs efficiently and achieve the best possible results. Good investment of your
time!

Here are some best practices for using LLMs:

- Be responsible. Use AI for Good.
- Be specific. The more specific your query, the best results you will get.
- Add [magical "Let’s think step by step" instruction](https://arxiv.org/pdf/2205.11916.pdf) to your prompt.
- Specify steps to achieve the desired goal yourself. This will make the LLM do what you want it to do.
- Provide examples. Sometimes it is best to show LLM a few examples of what you want instead of trying to explain it.
- Ask LLM to provide structured output (JSON, XML, etc). This way you can parse response more easily and distinguish
  different parts of it.
- Use unusual delimiters, such as \```triple backticks``` to help the LLM distinguish
  data or input from instructions.
