---
sidebar_position: 15
---

# Qianfan
[百度智能云千帆大模型](https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application)
![image](https://github.com/langchain4j/langchain4j/assets/95265298/600f8006-4484-4a75-829c-c8c16a3130c2)


## Maven Dependency(Maven依赖)

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>0.34.0</version>
</dependency>

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-qianfan</artifactId>
    <version>0.34.0</version>
</dependency>
```


## QianfanChatModel
[千帆所有模型及付费状态](https://console.bce.baidu.com/qianfan/ais/console/onlineService)
```java
  QianfanChatModel model = QianfanChatModel.builder()
          .apiKey("apiKey")
          .secretKey("secretKey")
          .modelName("Yi-34B-Chat") // 一个免费的模型名称 
          .build();

   String answer = model.generate("雷军");

   System.out.println(answer)
```
### Customizing

```java
QianfanChatModel model = QianfanChatModel.builder()
    .baseUrl(...)
    .apiKey(...)
    .secretKey(...)
    .temperature(...)
    .maxRetries(...)
    .topP(...)
    .modelName(...)
    .endpoint(...)
    .responseFormat(...)
    .penaltyScore(...)
    .logRequests(...)
    .logResponses()
    .build();
```

See the description of some of the parameters above [here](https://console.bce.baidu.com/tools/?u=qfdc#/api?product=QIANFAN&project=%E5%8D%83%E5%B8%86%E5%A4%A7%E6%A8%A1%E5%9E%8B%E5%B9%B3%E5%8F%B0&parent=Yi-34B-Chat&api=rpc%2F2.0%2Fai_custom%2Fv1%2Fwenxinworkshop%2Fchat%2Fyi_34b_chat&method=post).
### functions
**IAiService(重点)**
```java
public interface IAiService {
    /**
     * Ai Services 提供了一种更简单、更灵活的替代方案。 您可以定义自己的 API（具有一个或多个方法的 Java 接口）， 并将为其提供实现。
     * @param userMessage
     * @return String
     */
    String chat(String userMessage);
}
```
#### QianfanChatWithOnePersonMemory (带有一个人的聊天记忆)

```java

  QianfanChatModel model = QianfanChatModel.builder()
          .apiKey("apiKey")
          .secretKey("secretKey")
          .modelName("Yi-34B-Chat")
          .build();
  /* MessageWindowChatMemory
     functions as a sliding window, retaining the N most recent messages and evicting older ones that no longer fit.
     However, because each message can contain a varying number of tokens, MessageWindowChatMemory is mostly useful for fast prototyping.
     保留最新的n条消息(包括回复)
   */
  /* TokenWindowChatMemory
    which also operates as a sliding window but focuses on keeping the N most recent tokens, evicting older messages as needed. Messages are indivisible.
    If a message doesn't fit, it is evicted completely.
    MessageWindowChatMemory requires a Tokenizer to count the tokens in each ChatMessage.
  */
  ChatMemory chatMemory = MessageWindowChatMemory.builder()
          .maxMessages(10)
          .build();

  IAiService assistant = AiServices.builder(IAiService.class)
          .chatLanguageModel(model) // the model
          .chatMemory(chatMemory)  // memory
          .build();
        String answer = assistant.chat("Hello,my name is xiaoyu");
        System.out.println(answer); // Hello xiaoyu!******

        String answerWithName = assistant.chat("What's my name?");
        System.out.println(answerWithName); // Your name is xiaoyu.******

        String answer1 = assistant.chat("I like playing football.");
        System.out.println(answer1); // The answer

        String answer2 = assistant.chat("I want to go eat delicious food.");
        System.out.println(answer2); // The answer

        String answerWithLike = assistant.chat("What I like to do?");
        System.out.println(answerWithLike);//Playing football.******
```

#### QianfanChatWithMorePersonMemory (带有多个人的聊天记忆)

```java
  QianfanChatModel model = QianfanChatModel.builder()
          .apiKey("apiKey")
          .secretKey("secretKey")
          .modelName("Yi-34B-Chat")
          .build();
  IAiService assistant = AiServices.builder(IAiService.class)
          .chatLanguageModel(model)         // the model
          .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10)) // chatMemory
          .build();

  String answer = assistant.chat(1,"Hello, my name is xiaoyu");
  System.out.println(answer); // Hello xiaoyu!******
  String answer1 = assistant.chat(2,"Hello, my name is xiaomi");
  System.out.println(answer1); // Hello xiaomi!******

  String answerWithName1 = assistant.chat(1,"What's my name?");
  System.out.println(answerWithName1); // Your name is xiaoyu.
  String answerWithName2 = assistant.chat(2,"What's my name?");
  System.out.println(answerWithName2); // Your name is xiaomi.
```

#### QianfanChatWithPersistentMemory(持久化聊天记忆)

```xml
    <dependency>
        <groupId>org.mapdb</groupId>
        <artifactId>mapdb</artifactId>
        <version>3.1.0</version>
    </dependency>
```
```java
class PersistentChatMemoryStore implements ChatMemoryStore {
    private final DB db = DBMaker.fileDB("chat-memory.db").transactionEnable().make();
    private final Map<String, String> map = db.hashMap("messages", STRING, STRING).createOrOpen();

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = map.get((String) memoryId);
        return messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String json = messagesToJson(messages);
        map.put((String) memoryId, json);
        db.commit();
    }

    @Override
    public void deleteMessages(Object memoryId) {
        map.remove((String) memoryId);
        db.commit();
    }
}

class PersistentChatMemoryTest{
  public void test(){
    QianfanChatModel chatLanguageModel = QianfanChatModel.builder()
            .apiKey("apiKey")
            .secretKey("secretKey")
            .modelName("Yi-34B-Chat")
            .build();
    
    ChatMemory chatMemory = MessageWindowChatMemory.builder()
            .maxMessages(10)
            .chatMemoryStore(new PersistentChatMemoryStore())
            .build();
    
    IAiService assistant = AiServices.builder(IAiService.class)
            .chatLanguageModel(chatLanguageModel)
            .chatMemory(chatMemory)
            .build();
    
    String answer = assistant.chat("My name is xiaoyu");
    System.out.println(answer);
    // Run it once and then comment the top to run the bottom(运行一次后注释上面运行下面)
    // String answerWithName = assistant.chat("What is my name?");
    // System.out.println(answerWithName);
  }
}

```

#### QianfanStreamingChatModel(流式回复)
LLMs generate text one token at a time, so many LLM providers offer a way to stream the response token-by-token instead of waiting for the entire text to be generated. This significantly improves the user experience, as the user does not need to wait an unknown amount of time and can start reading the response almost immediately.（因此许多LLM提供者提供了一种逐个token地传输响应的方法，而不是等待生成整个文本。这极大地改善了用户体验，因为用户不需要等待未知的时间，几乎可以立即开始阅读响应。）
以下是一个通过StreamingResponseHandler来实现
```java
  QianfanStreamingChatModel qianfanStreamingChatModel = QianfanStreamingChatModel.builder()
          .apiKey("apiKey")
          .secretKey("secretKey")
          .modelName("Yi-34B-Chat")
          .build();
  qianfanStreamingChatModel.generate(userMessage, new StreamingResponseHandler<AiMessage>() {
        @Override
        public void onNext(String token) {
            System.out.print(token);
        }
        @Override
        public void onComplete(Response<AiMessage> response) {
            System.out.println("onComplete: " + response);
        }
        @Override
        public void onError(Throwable throwable) {
            throwable.printStackTrace();
        }
  });
```
以下是另一个通过TokenStream来实现
```java
  QianfanStreamingChatModel qianfanStreamingChatModel = QianfanStreamingChatModel.builder()
          .apiKey("apiKey")
          .secretKey("secretKey")
          .modelName("Yi-34B-Chat")
          .build();
  IAiService assistant = AiServices.create(IAiService.class, qianfanStreamingChatModel);
  
  TokenStream tokenStream = assistant.chatInTokenStream("Tell me a story.");
  tokenStream.onNext(System.out::println)
          .onError(Throwable::printStackTrace)
          .start();
```
#### QianfanRAG

程序自动将匹配的内容与用户问题组装成一个Prompt，向大语言模型提问，大语言模型返回答案

LangChain4j has an "Easy RAG" feature that makes it as easy as possible to get started with RAG. You don't have to learn about embeddings, choose a vector store, find the right embedding model, figure out how to parse and split documents, etc. Just point to your document(s), and LangChain4j will do its magic.

- Import the dependency:langchain4j-easy-rag
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-easy-rag</artifactId>
    <version>0.34.0</version>
</dependency>
```
- Use
```java

  QianfanChatModel chatLanguageModel = QianfanChatModel.builder()
        .apiKey(API_KEY)
        .secretKey(SECRET_KEY)
        .modelName("Yi-34B-Chat")
        .build();
  // All files in a directory, txt seems to be faster
  List<Document> documents = FileSystemDocumentLoader.loadDocuments("/home/langchain4j/documentation");
  // for simplicity, we will use an in-memory one:
  InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
  EmbeddingStoreIngestor.ingest(documents, embeddingStore);

  IAiService assistant = AiServices.builder(IAiService.class)
          .chatLanguageModel(chatLanguageModel)
          .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
          .contentRetriever(EmbeddingStoreContentRetriever.from(embeddingStore))
          .build();

  String answer = assistant.chat("The Question");
  System.out.println(answer);

```


## Examples

- [Qianfan Examples](https://github.com/langchain4j/langchain4j/tree/main/langchain4j-qianfan/src/test/java/dev/langchain4j/model/qianfan)
