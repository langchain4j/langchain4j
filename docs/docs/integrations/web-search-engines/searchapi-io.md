---
sidebar_position: 1
---

# SearchApi

- [SearchApi](https://www.searchapi.io/)


## Maven Dependency
If you are using Maven, add this snippet to your `pom.xml`:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-web-search-engine-searchapi</artifactId>
    <version>${version}</version>
</dependency>
```

If you are using Gradle, add this snippet to your `build.gradle`:
```sh
implementation "dev.langchain4j:langchain4j-web-search-engine-searchapi:$version"
```

The current `$version` is: `0.33.0`.


## Usage

To perform a web search using the default search engine (Google Search):
```java

WebSearchEngine webSearchEngine = SearchApiWebSearchEngine.builder()
    .apiKey(System.getenv("SEARCHAPI_API_KEY"))
    .logRequests(true)
    .build();

WebSearchResults webSearchResults = webSearchEngine.search("chatgpt");
```

You can also use the `SearchApiWebSearchEngine()` constructor directly. This code is equivalent to the snippet above:
```java
WebSearchEngine webSearchEngine = new SearchApiWebSearchEngine(
    System.getenv("SEARCHAPI_API_KEY"), 
    "google",
    Duration.ofSeconds(30), 
    true,
    null);

WebSearchResults webSearchResults = webSearchEngine.search("chatgpt");
```

Without any customization, all searches default to the `google.com` domain. 

To use a different Google domain or specify different parameters in a search, you have to customize the request parameters.


### Customizing Request Parameters

There are two ways by which you can customize the request parameters prior to a search:
* providing a [`java.util.function.Consumer`](https://docs.oracle.com/javase/8/docs/api/java/util/function/Consumer.html) function that will `accept(T)` a `T` of type `<Map<String, Object>> params` when you use the `SearchApiWebSearchEngine()` constructor or;
* using an anonymous subclass that overrides the life cycle method `SearchApiWebSearchEngine#customizeSearchRequest(final SearchApiRequest request, final WebSearchRequest webSearchRequest)`.

 

Below are examples in the 2 supported styles showing how to customize three parameters. 

Each snippet sets the:
* `google_domain` parameter to use the `google.co.uk` domain (defaults to `google`);
* `device` parameter to `mobile` (defaults to `desktop`);
* `safe` to `active` to turn on safe search (defaults to `off`).

 
#### 1st Style: Consumer Function
```java

WebSearchEngine webSearchEngine = new SearchApiWebSearchEngine(
    System.getenv("SEARCHAPI_API_KEY"), 
    "google",
    Duration.ofSeconds(30), 
    true,
    (params) -> {
    	params.put("device", "mobile");
    	params.put("google_domain", "google.co.uk");
    	params.put("safe", "active");
    }
);

WebSearchResults webSearchResults = webSearchEngine.search("chatgpt");
```


#### 2nd Style: Anonymous Subclass
```java

WebSearchEngine webSearchEngine = new SearchApiWebSearchEngine(System.getenv("SEARCHAPI_API_KEY"), "google") {
		
    @Override
    protected void customizeSearchRequest(final SearchApiRequest request, final WebSearchRequest webSearchRequest) {
    	super.customizeSearchRequest(request, webSearchRequest);
    	
    	request.getParams().put("device", "mobile");
    	request.getParams().put("google_domain", "google.co.uk");
    	request.getParams().put("safe", "active");
    }
};
		
WebSearchResults webSearchResults = searchapiWebSearchEngine.search("chatgpt");
```


## Search Engines

Any search engine on SearchApi that returns an `organic_results` array with each element containing a `title`, `link` and `snippet` is supported. 

Search engines that return `organic_results` in this format include:
* `google`
* `bing` 
* `baidu` 
* `bing_news` 
* `google_news` 
* `google_scholar` etc

For the full list of supported search engines, please refer to the [documentation](https://www.searchapi.io/docs/google).


The following web search engine have been tested and are used in the linked Usage Examples:
- `google` - [Google Search API](https://www.searchapi.io/docs/google)
- `bing` - [Bing Search API](https://www.searchapi.io/docs/bing)
- `baidu` - [Baidu Search API](https://www.searchapi.io/docs/baidu)



## Full Example

```java
// Partially adapted from: 
// https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithToolsExample.java 
// and: 
// https://github.com/langchain4j/langchain4j/blob/feat/search-api/web-search-engines/langchain4j-web-search-engine-searchapi/src/test/java/dev/langchain4j/web/search/searchapi/SearchApiWebSearchToolIT.java

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchTool;
import dev.langchain4j.web.search.WebSearchToolIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SearchWithToolsExample {

    static final boolean logRequests = true;

    WebSearchEngine webSearchEngine = SearchApiWebSearchEngine.withApiKey(System.getenv("SEARCHAPI_API_KEY"));

    ChatLanguageModel chatModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName(OpenAiChatModelName.GPT_3_5_TURBO)
            .logRequests(logRequests)
            .build();

    interface Assistant {
        @dev.langchain4j.service.SystemMessage({
                "You are a web search support agent.",
                "If there is any event that has not happened yet",
                "You MUST create a web search request with user query and",
                "use the web search tool to search the web for organic web results.",
                "Include the source link in your final response."
        })
        String answer(String userMessage);
    }


    public static void main(String[] args) {
        WebSearchTool webTool = WebSearchTool.from(webSearchEngine);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatModel)
                .tools(webTool)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        String answer = assistant.answer("Search in the web who won the FIFA World Cup 2022?");

        // then
        assertThat(answer).containsIgnoringCase("Argentina");    
    }
}

```


## Additional Usage Examples

These integration tests contain additional usage examples of how to use this module as a `WebSearchEngine` or as a `Tool`:

- [SearchApiWebSearchEngineIT.java](https://github.com/langchain4j/langchain4j/blob/feat/search-api/web-search-engines/langchain4j-web-search-engine-searchapi/src/test/java/dev/langchain4j/web/search/searchapi/SearchApiWebSearchEngineIT.java)
- [SearchApiWebSearchToolIT.java](https://github.com/langchain4j/langchain4j/blob/feat/search-api/web-search-engines/langchain4j-web-search-engine-searchapi/src/test/java/dev/langchain4j/web/search/searchapi/SearchApiWebSearchToolIT.java)
- [SearchApiWebSearchContentRetrieverIT.java](https://github.com/langchain4j/langchain4j/blob/feat/search-api/web-search-engines/langchain4j-web-search-engine-searchapi/src/test/java/dev/langchain4j/web/search/searchapi/SearchApiWebSearchContentRetrieverIT.java)
