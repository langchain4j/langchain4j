---
sidebar_position: 13
---

# Classification

## **Overview**
This documentation provides an implementation of a classification system using **LangChain4j** in Java. Classification is essential for categorizing text into predefined labels, such as **sentiment analysis, intent detection,** and **entity recognition**.

This example demonstrates **sentiment classification** using LangChain4j's AI-powered services.

---

LangChain4j supports two common approaches to text classification:

- Use an LLM through **AI Services** when labels depend on nuanced natural language reasoning.
- Use **embeddings** through `TextClassifier` and `EmbeddingModelTextClassifier` when you have labeled examples
  for each category and want to classify by semantic similarity.

## **Sentiment Classification Service**
The sentiment classification system categorizes input text into one of the following **sentiment categories**:
- **POSITIVE**
- **NEUTRAL**
- **NEGATIVE**

### **Implementation**
```java
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;

public class SentimentClassification {

    // Initialize the chat model using OpenAI
    static ChatModel chatModel = OpenAiChatModel.withApiKey("YOUR_OPENAI_API_KEY");

    // Define the Sentiment enum
    enum Sentiment {
        POSITIVE, NEUTRAL, NEGATIVE
    }

    // Define the AI-powered Sentiment Analyzer interface
    interface SentimentAnalyzer {

        @UserMessage("Analyze sentiment of {{it}}")
        Sentiment analyzeSentimentOf(String text);

        @UserMessage("Does {{it}} have a positive sentiment?")
        boolean isPositive(String text);
    }

    public static void main(String[] args) {

        // Create an AI-powered Sentiment Analyzer instance
        SentimentAnalyzer sentimentAnalyzer = AiServices.create(SentimentAnalyzer.class, chatModel);

        // Example Sentiment Analysis
        Sentiment sentiment = sentimentAnalyzer.analyzeSentimentOf("I love this product!");
        System.out.println(sentiment); // Expected Output: POSITIVE

        boolean positive = sentimentAnalyzer.isPositive("This is a terrible experience.");
        System.out.println(positive); // Expected Output: false
    }
}
```

---

## **Explanation of Components**

### **1. Chat Model Initialization**
```java
static ChatModel chatModel = OpenAiChatModel.withApiKey("YOUR_OPENAI_API_KEY");
```
- Initializes the **OpenAI Chat Model** to process natural language text.
- Replace `"YOUR_OPENAI_API_KEY"` with an actual OpenAI API key.

### **2. Defining Sentiment Categories**
```java
enum Sentiment {
    POSITIVE, NEUTRAL, NEGATIVE
}
```
- The `Sentiment` enum represents possible sentiment classifications.

### **3. Creating the AI-Powered Sentiment Analyzer**
```java
interface SentimentAnalyzer {
    
    @UserMessage("Analyze sentiment of {{it}}")
    Sentiment analyzeSentimentOf(String text);

    @UserMessage("Does {{it}} have a positive sentiment?")
    boolean isPositive(String text);
}
```
- This interface defines two AI-powered methods:
    - `analyzeSentimentOf(String text)`: Classifies a given text as **POSITIVE, NEUTRAL,** or **NEGATIVE**.
    - `isPositive(String text)`: Returns `true` if the text has a positive sentiment; otherwise, `false`.

### **4. Creating an AI Service Instance**
```java
SentimentAnalyzer sentimentAnalyzer = AiServices.create(SentimentAnalyzer.class, chatModel);
```
- `AiServices.create()` dynamically implements the `SentimentAnalyzer` interface using the AI model.

### **5. Running Sentiment Analysis**
```java
Sentiment sentiment = sentimentAnalyzer.analyzeSentimentOf("I love this product!");
System.out.println(sentiment); // Output: POSITIVE

boolean positive = sentimentAnalyzer.isPositive("This is a terrible experience.");
System.out.println(positive); // Output: false
```
- The AI model classifies a given text into one of the predefined sentiment categories.
- The `isPositive()` method provides a boolean result.

---

## **Embedding-Based Classification**

`EmbeddingModelTextClassifier` classifies text by embedding the input and comparing it with embedded examples for each
label. This approach can be useful when you can provide representative examples for every class and do not need an LLM
call for each classification request.

```java
import dev.langchain4j.classification.EmbeddingModelTextClassifier;
import dev.langchain4j.classification.TextClassifier;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;

import java.util.List;
import java.util.Map;

public class EmbeddingBasedSentimentClassification {

    enum Sentiment {
        POSITIVE, NEUTRAL, NEGATIVE
    }

    public static void main(String[] args) {

        Map<Sentiment, List<String>> examples = Map.of(
                Sentiment.POSITIVE, List.of("This is great!", "I love this product."),
                Sentiment.NEUTRAL, List.of("It is okay.", "This works as expected."),
                Sentiment.NEGATIVE, List.of("This is terrible.", "I am disappointed."));

        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        TextClassifier<Sentiment> classifier = new EmbeddingModelTextClassifier<>(embeddingModel, examples);

        List<Sentiment> sentiments = classifier.classify("Awesome experience!");
        System.out.println(sentiments); // [POSITIVE]
    }
}
```

You can also use `classifyWithScores(...)` when you need the similarity score for each returned label. The classifier
can return zero, one, or multiple labels depending on its `maxResults`, `minScore`, and `meanToMaxScoreRatio` settings.

---

## **Use Cases**
This sentiment classification service can be used in various applications, including:

✅ **Customer Feedback Analysis**: Classify customer reviews as positive, neutral, or negative.  
✅ **Social Media Monitoring**: Analyze sentiment trends in social media comments.  
✅ **Chatbot Responses**: Understand user sentiment to provide better responses.


## Examples

- [Example of classification using LLM](https://github.com/langchain4j/langchain4j-examples/blob/5c5fc14613101a84fe32b39200e30701fec45194/other-examples/src/main/java/OtherServiceExamples.java#L27)
- [Example of classification using embeddings](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/embedding/classification/EmbeddingModelTextClassifierExample.java)
