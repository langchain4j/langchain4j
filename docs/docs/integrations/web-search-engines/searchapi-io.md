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

To perform a web search using the default search engine: [Google Search API](https://www.searchapi.io/docs/google):
```java
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.searchapi.SearchApiWebSearchEngine;


// use the one-liner convenience method
WebSearchEngine webSearchEngine = SearchApiWebSearchEngine.withApiKey(System.getenv("SEARCHAPI_API_KEY"));

WebSearchResults webSearchResults = webSearchEngine.search("chatgpt");
```

The only required property is an `apiKey`, the `WebSearchEngine` will use reasonable defaults for everything else.


### Logging

If you want to enable logging of requests and responses from SearchApi, you will need to set `logRequests` to `true` (defaults to `false`).
```java
// use the fluent interface aka method chaining
WebSearchEngine webSearchEngine = SearchApiWebSearchEngine.builder()
    .apiKey(System.getenv("SEARCHAPI_API_KEY"))
    .logRequests(true)
    .build();

WebSearchResults webSearchResults = webSearchEngine.search("chatgpt");
```


You can also use the `SearchApiWebSearchEngine()` constructor directly. This code is equivalent to the snippet above:
```java
// use the constructor method
WebSearchEngine webSearchEngine = new SearchApiWebSearchEngine(
    System.getenv("SEARCHAPI_API_KEY"), 
    "google",
    Duration.ofSeconds(30), 
    true,
    null);

WebSearchResults webSearchResults = webSearchEngine.search("chatgpt");
```



## Customization

Without any customization, all searches using a `SearchApiWebSearchEngine` instance defaults to the `google.com` domain.

The `SearchApiWebSearchEngine` class offers two life cycle methods that can be used to customize search requests and search results:
* `void customizeSearchRequest(final SearchApiRequest request, final WebSearchRequest webSearchRequest);`
* `void customizeSearchResults(final SearchApiResponse response, final List<WebSearchOrganicResult> results)`

The `customizeSearchRequest()` method is called by `SearchApiWebSearchEngine` just before a search is performed. Overriding this method will allow you to customize the parameters of a search request.


The `customizeSearchResults()` method is called by `SearchApiWebSearchEngine` just after a search is performed but before the results are sent back to the caller. Overriding this method will allow you to customize the search results (e.g. re-ordering of elements).



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

 
#### 1st Style: Consumer aka Functional Interface
```java
// use the Consumer functional interface
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
// uses an anonymous subclass
WebSearchEngine webSearchEngine = new SearchApiWebSearchEngine(System.getenv("SEARCHAPI_API_KEY"), "google") {
		
    @Override
    protected void customizeSearchRequest(final SearchApiRequest request, final WebSearchRequest webSearchRequest) {
    	super.customizeSearchRequest(request, webSearchRequest);
    	
    	request.getParams().put("device", "mobile");
    	request.getParams().put("google_domain", "google.co.uk");
    	request.getParams().put("safe", "active");
    }
};
		
WebSearchResults webSearchResults = webSearchEngine.search("chatgpt");
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
- `google`
- `bing`
- `baidu`


### Bing Search Engine

* [Bing Search API](https://www.searchapi.io/docs/bing)

This snippet illustrates how to create a `SearchApiWebSearchEngine` that uses the `bing` search engine:
```java
// uses an anonymous subclass
SearchApiWebSearchEngine webSearchEngine = new SearchApiWebSearchEngine(System.getenv("SEARCHAPI_API_KEY"), "bing") {
    @Override
    protected void customizeSearchRequest(final SearchApiRequest request, final WebSearchRequest webSearchRequest) {
        request.getParams().put("device", "tablet");
        request.getParams().put("language", "en");
        request.getParams().put("safe_search", "strict");
        request.getParams().put("num", "10");
        request.getParams().put("page", "1");
    }
};


```

### Baidu Search Engine

* [Baidu Search API](https://www.searchapi.io/docs/baidu)

This example shows how to use `baidu` as a search engine with the language parameter (`ct`) [explicitly set](https://www.searchapi.io/docs/baidu#api-parameters-localization-ct) to `Simplified and Traditional Chinese`:
```java
// use the Consumer functional interface
SearchApiWebSearchEngine webSearchEngine = SearchApiWebSearchEngine.builder()
        .apiKey(System.getenv("SEARCHAPI_API_KEY"))
        .engine("baidu")
        .logRequests(true)
        .customizeParametersFunc(
                (params) -> {
                    params.put("ct", "0");
                    params.put("num", "5");
                    params.put("page", "1");
                })
        .build();

WebSearchResults webSearchResults = webSearchEngine.search("chatgpt");
List<WebSearchOrganicResult> results = webSearchResults.results();
System.out.println(results);

/*
[WebSearchOrganicResult{title='ChatGPT: Optimizing Language Models for Dialogue 官方', url=https://openai.com/blog/chatgpt/, snippet='We’ve trained a model called ChatGPT which interacts in a conversational way. The dialogue format makes it possible for ChatGPT to answer...', content='null', metadata={position=1}}, 

WebSearchOrganicResult{title='爆火的ChatGPT,被小学生打败了', url=https://baijiahao.baidu.com/s?id=1751463938792563285&wfr=spider&for=pc, snippet='虽说ChatGPT做小学考试题时表现得智商堪忧,但这不妨碍它在回答一些专业问题时地高水准发挥。数据科学公司Anaconda的创始人兼CEO Peter Wang亦给予了ChatGPT超高的评价:“我刚刚跟ChatGPT足足聊了2...', content='null', metadata={position=2}}, 

WebSearchOrganicResult{title='chatgpt在哪里下载? - 知乎', url=https://www.zhihu.com/question/632952940/answer/3344265698, snippet='https://chat.openai.com/​chat.openai.com/ 通过官网，无论是手机、平板、电脑都可以直接使用Chat...', content='null', metadata={position=3}}, 

WebSearchOrganicResult{title='ChatGPT国内如何使用!全网最全ChatGPT注册使用教程,GPT底...', url=https://www.bilibili.com/video/BV1GKa4eiEwF/, snippet='耗时2天半,给你们找到一个免费无限制使用的ChatGPT4.0网站,分享给有需要的人。 黑科技测评君 935 0 国内可免费无限制使用ChatGPt4.0网站,分享给有需要的人 黑科技差评...', content='null', metadata={position=4}}]

*/        
```


## OpenAI Usage Example

```java
// Partially adapted from: 
// https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithToolsExample.java 
// and: 
// https://github.com/langchain4j/langchain4j/blob/main/web-search-engines/langchain4j-web-search-engine-searchapi/src/test/java/dev/langchain4j/web/search/searchapi/SearchApiWebSearchToolIT.java

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

- [SearchApiWebSearchEngineIT.java](https://github.com/langchain4j/langchain4j/blob/main/web-search-engines/langchain4j-web-search-engine-searchapi/src/test/java/dev/langchain4j/web/search/searchapi/SearchApiWebSearchEngineIT.java)
- [SearchApiWebSearchToolIT.java](https://github.com/langchain4j/langchain4j/blob/main/web-search-engines/langchain4j-web-search-engine-searchapi/src/test/java/dev/langchain4j/web/search/searchapi/SearchApiWebSearchToolIT.java)
- [SearchApiWebSearchContentRetrieverIT.java](https://github.com/langchain4j/langchain4j/blob/main/web-search-engines/langchain4j-web-search-engine-searchapi/src/test/java/dev/langchain4j/web/search/searchapi/SearchApiWebSearchContentRetrieverIT.java)
