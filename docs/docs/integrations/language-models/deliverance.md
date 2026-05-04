# Deliverance
[Deliverance Home](https://github.com/edwardcapriolo/deliverance/blob/main/README.md)

Deliverance is Java inference engine which make it well suited for pairing with Langchain4j. Deliverance supports many
of the standard features like temperature and top_p, and also supports many advanced options like Exclude Top Choice (xtc)
and KVCache salting.

## Requirements
- JDK25

## Embedded mode
This integration is running in embedded mode. Meaning the engine will run locally inside the JVM
that launches the application.

Enable jdk.incubator.vectory support:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <forkCount>1</forkCount>
        <argLine>-XX:-UseCompactObjectHeaders --add-opens java.base/java.nio=ALL-UNNAMED 
            --add-modules jdk.incubator.vector -Djdk.attach.allowAttachSelf=true 
            --enable-native-access=ALL-UNNAMED</argLine>
    </configuration>
</plugin>

```

## Simple Usage ##

Deliverance has a wide array of options, some of them are engine options that only apply when the model is created 
such as tuning the kv-buffer cache. Those can be done by customizing with AutoModelForCausualLm.builder.

Meanwhile the DeliveranceChatModel allows customization of request attributes like temperature. 

```java
AutoModelForCausaLm.Builder builder = DeliveranceModels.builder((Path) null,
        DeliveranceTestUtils.GEMMA_MODEL_NAME);
builder.withTensorProvider(new ConfigurableTensorProvider(builder.getAllocator(),
        new WrappedForkJoinPool(WrappedForkJoinPool.autoSizeByCores())));

try (model = DeliveranceChatModel.builder()
        .modelBuilder(builder)
        .defaultRequestParameters(parameters -> parameters
                .temperature(0.0)
                .topP(0.9)
                .maxOutputTokens(64))
        .build()){
            List<ChatMessage> messages = singletonList(UserMessage.from("When is the best time of year to visit Japan?"));
        }
```

## Chat options

```java
        DeliveranceChatRequestParameters parameters = DeliveranceChatRequestParameters.builder()
                .temperature(0.1)
                .topP(0.8)
                .topK(12)
                .maxOutputTokens(42)
                .stopSequences("END")
                .ntokens(11)
                .seed(13)
                .guidedChoice("a", "b")
                .logProbs(true)
                .topLogProbs(3)
                .xtcThreshold(0.2)
                .xtcProbability(0.4)
                .includeStopStrInOutput(true)
                .build();
```
