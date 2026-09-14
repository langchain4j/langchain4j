---
sidebar_position: 0
---

# Brave Search

[Brave Search](https://search.brave.com/) is an independent search engine with a paid [Search API](https://api-dashboard.search.brave.com/documentation) that can be used to perform web searches.

## Maven Dependency

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-web-search-engine-brave</artifactId>
    <version>1.19.0-beta29</version>
</dependency>
```

## APIs

- `BraveWebSearchEngine`

## Examples

```java
import dev.langchain4j.web.search.WebSearchTool;
import dev.langchain4j.web.search.brave.BraveWebSearchEngine;

WebSearchTool webSearchTool = WebSearchTool.from(
        BraveWebSearchEngine.builder()
                .apiKey(System.getenv("BRAVE_API_KEY"))
                .build());
```
