---
sidebar_position: 12
---

# Classification

## **Overview**
This documentation provides an implementation of a classification system using **LangChain4j** in Java. Classification is essential for categorizing text into predefined labels, such as **sentiment analysis, intent detection,** and **entity recognition**.

This example demonstrates **sentiment classification** using LangChain4j's AI-powered services.

---

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

## **Use Cases**
This sentiment classification service can be used in various applications, including:

✅ **Customer Feedback Analysis**: Classify customer reviews as positive, neutral, or negative.  
✅ **Social Media Monitoring**: Analyze sentiment trends in social media comments.  
✅ **Chatbot Responses**: Understand user sentiment to provide better responses.


## Examples

- [Example of classification using LLM](https://github.com/langchain4j/langchain4j-examples/blob/5c5fc14613101a84fe32b39200e30701fec45194/other-examples/src/main/java/OtherServiceExamples.java#L27)
- [Example of classification using embeddings](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/embedding/classification/EmbeddingModelTextClassifierExample.java)
