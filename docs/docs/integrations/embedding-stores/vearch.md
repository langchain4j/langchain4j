---
sidebar_position: 24
---

# Vearch

https://github.com/vearch/vearch


## Maven Dependency

:::note
Since `1.0.0-alpha1`, `langchain4j-vearch` has migrated to `langchain4j-community` and is renamed to `langchain4j-community-vearch`.
:::

`0.36.2` and previous:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-vearch</artifactId>
    <version>1.0.1-beta6</version>
</dependency>
```

`1.0.0-alpha1` and later:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-vearch</artifactId>
    <version>${latest version here}</version>
</dependency>
```

Or, you can use BOM to manage dependencies consistently:

```xml
<dependencyManagement>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-community-bom</artifactId>
        <version>${latest version here}</version>
        <typ>pom</typ>
        <scope>import</scope>
    </dependency>
</dependencyManagement>
```

## APIs

### `1.0.0-alpha1` and previous

:::note
* `1.0.0-alpha1` and previous `langchain4j-vearch` uses `Vearch` [old api](https://vearch.readthedocs.io/zh-cn/v3.3.x/overview.html), which is deprecated in vearch 3.4.x version. 
* `1.0.0-alpha1` and later `langchain4j-community-vearch` uses `Vearch` [latest api](https://vearch.readthedocs.io/zh-cn/latest/overview.html), which supports 3.5.x and 3.4.x version.

We recommend to use `langchain4j-community-vearch` which supports the latest version of `Vearch`.
:::

To use `VearchEmbeddingStore`, you need to instantiate a `VearchConfig`:

```java
String embeddingFieldName = "text_embedding";
String textFieldName = "text";
Map<String, Object> metadata = createMetadata().toMap();

// init properties
Map<String, SpacePropertyParam> properties = new HashMap<>(4);
properties.put(embeddingFieldName, SpacePropertyParam.VectorParam.builder()
        .index(true)
        .storeType(SpaceStoreType.MEMORY_ONLY)
        .dimension(384)
        .build());
properties.put(textFieldName, SpacePropertyParam.StringParam.builder().build());
// put metadata... e.g. properties.put("name", SpacePropertyParam.StringParam.builder().build());

VearchConfig vearchConfig = VearchConfig.builder()
        .spaceEngine(SpaceEngine.builder()
                .name("gamma")
                .indexSize(1L)
                .retrievalType(RetrievalType.FLAT)
                .retrievalParam(RetrievalParam.FLAT.builder()
                        .build())
                .build())
        .properties(properties)
        .embeddingFieldName(embeddingFieldName)
        .textFieldName(textFieldName)
        .databaseName(databaseName)
        .spaceName(spaceName)
        .modelParams(singletonList(ModelParam.builder()
                .modelId("vgg16")
                .fields(singletonList("string"))
                .out("feature")
                .build()))
        .build();
```

Then, you can create a `VearchEmbeddingStore`:

```java
VearchEmbeddingStore embeddingStore = VearchEmbeddingStore.builder()
        .vearchConfig(vearchConfig)
        .baseUrl(baseUrl)
        .build();
```


### `1.0.0-alpha1` and later

To use `VearchEmbeddingStore`, you need to instantiate a `VearchConfig`:

```java
String embeddingFieldName = "text_embedding";
String textFieldName = "text";
String spaceName = "embedding_space_" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);

// init Fields
List<Field> fields = new ArrayList<>(4);
List<String> metadataFieldNames = new ArrayList<>();
fields.add(VectorField.builder()
        .name(embeddingFieldName)
        .dimension(embeddingModel.dimension())
        .index(Index.builder()
                .name("gamma")
                .type(IndexType.HNSW)
                .params(HNSWParam.builder()
                        .metricType(MetricType.INNER_PRODUCT)
                        .efConstruction(100)
                        .nLinks(32)
                        .efSearch(64)
                        .build())
                .build())
        .build()
);
fields.add(StringField.builder().name(textFieldName).fieldType(FieldType.STRING).build());
// put metadata... e.g. fields.add(StringField.builder().name("name").fieldType(FieldType.STRING).build());

VearchConfig vearchConfig = VearchConfig.builder()
        .databaseName(databaseName)
        .spaceName(spaceName)
        .textFieldName(textFieldName)
        .embeddingFieldName(embeddingFieldName)
        .fields(fields)
        .metadataFieldNames(metadataFieldNames)
        .searchIndexParam(HNSWSearchParam.builder()
                // Only support INNER_PRODUCT now
                .metricType(MetricType.INNER_PRODUCT)
                .efSearch(64)
                .build())
        .build();
```

Then, you can create a `VearchEmbeddingStore`:

```java
VearchEmbeddingStore embeddingStore = VearchEmbeddingStore.builder()
        .vearchConfig(vearchConfig)
        .baseUrl(baseUrl)
        .logRequests(true)
        .logResponses(true)
        .build();
```


## Examples

- [VearchEmbeddingStoreIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-vearch/src/test/java/dev/langchain4j/store/embedding/vearch/VearchEmbeddingStoreIT.java)
