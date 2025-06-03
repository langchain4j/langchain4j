---
sidebar_position: 9
---

# Oracle Coherence

https://coherence.community/

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-coherence</artifactId>
    <version>1.0.1-beta6</version>
</dependency>
```

The `langchain4j-coherence` module has Coherence as a provided dependency as it works with various Coherence versions.
Developers should include the relevant Coherence dependency, either Community Edition or Commercial version.
Coherence CE has a groupId of `com.oracle.coherence.ce` and commercial versions have a groupId of `com.oracle.coherence`.

For example, to use Community Edition (CE), add the Coherence BOM to the dependency management section then add Coherence as a dependency. Other Coherence modules can then be added to the project as required.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.oracle.coherence.ce</groupId>
            <artifactId>coherence-bom</artifactId>
            <version>24.09</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-coherence</artifactId>
        <version>1.0.1-beta6</version>
    </dependency>
    <dependency>
        <groupId>com.oracle.coherence.ce</groupId>
        <artifactId>coherence</artifactId>
    </dependency>
</dependencies>
```

## APIs

- `CoherenceEmbeddingStore`
- `CoherenceChatMemoryStore`

## Examples

- [CoherenceEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/oracle-coherence-example/src/main/java/CoherenceEmbeddingStoreExample.java)
