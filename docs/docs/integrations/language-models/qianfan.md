---
sidebar_position: 13
---

# Qianfan
[百度智能云千帆大模型](https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application)
![image](https://github.com/langchain4j/langchain4j/assets/95265298/600f8006-4484-4a75-829c-c8c16a3130c2)
## Maven Dependency(Maven依赖)

```xml

<dependency>
  <groupId>dev.langchain4j</groupId>
  <artifactId>langchain4j-qianfan</artifactId>
  <version>0.30.0</version>
</dependency>

```


## QianfanChatModel
[千帆所有模型及付费状态](https://console.bce.baidu.com/qianfan/ais/console/onlineService)
```java

  QianfanChatModel model = QianfanChatModel.builder()
          .apiKey("your apiKey(千帆大模型的apikey)")
          .secretKey("your secretKey(千帆大模型的secretKey)")
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

### Qianfan
![image](https://github.com/langchain4j/langchain4j/assets/95265298/8056b2f7-97a4-4840-bcf7-9b16b7186f0a)

程序自动将匹配的内容与用户问题组装成一个Prompt，向大语言模型提问，大语言模型返回答案
Ingests specified Documents into a specified EmbeddingStore.
Uses DocumentSplitter and EmbeddingModel found through SPIs
For the "Easy RAG", import langchain4j-easy-rag module, which contains a DocumentSplitterFactory and EmbeddingModelFactory implementations.
```java

  QianfanChatModel model = QianfanChatModel.builder()
        .apiKey(API_KEY)
        .secretKey(SECRET_KEY)
        .modelName("Yi-34B-Chat")
        .build();
  
  AllMiniLmL6V2EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
  EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
  EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
          .documentSplitter(DocumentSplitters.recursive(900000000,0))
          .embeddingModel(embeddingModel)
          .embeddingStore(embeddingStore)
          .build();
  
  // 一个目录下的所有文件，txt貌似更快
  List<Document> documents = loadDocuments(Path.of("D:/simpleTest/"), new TextDocumentParser());
  ingestor.ingest(documents);
  
  ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
          .chatLanguageModel(model)
          .retriever(EmbeddingStoreRetriever.from(embeddingStore, embeddingModel))
          // .chatMemory() // you can override default chat memory
          // .promptTemplate() // you can override default prompt template
          .build();
  System.out.println(chain.execute(ans))

```
