---
sidebar_position: 1
---

# SearchApi

[SearchApi](https://www.searchapi.io/) is a real-time SERP API. You can use it to perform searches in Google, Google News, Bing, Bing News, Baidu, Google Scholar, or any other engine that returns organic results.

## Usage

### Dependencies setup

Add the following dependencies to your project's `pom.xml`:
```xml
<dependency>
  <groupId>dev.langchain4j</groupId>
  <artifactId>langchain4j-web-search-engine-searchapi</artifactId>
  <version>0.34.0</version>
</dependency>
```

or project's `build.gradle`:

```groovy
implementation 'dev.langchain4j:langchain4j-web-search-engine-searchapi:0.34.0'
```

### Example code:

```java
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.web.search.WebSearchTool;
import dev.langchain4j.web.search.searchapi.SearchApiEngine;
import dev.langchain4j.web.search.searchapi.SearchApiWebSearchEngine;

public class SearchApiTool {

    interface Assistant {
        @SystemMessage({
                "You are a web search support agent.",
                "If there is any event that has not happened yet",
                "You MUST create a web search request with user query and",
                "use the web search tool to search the web for organic web results.",
                "Include the source link in your final response."
        })
        String answer(String userMessage);
    }

    private static final String SEARCHAPI_API_KEY = "YOUR_SEARCHAPI_KEY";
    private static final String OPENAI_API_KEY = "YOUR_OPENAI_KEY";

    public static void main(String[] args) {
        Map<String, Object> optionalParameters = new HashMap<>();
        optionalParameters.put("gl", "us");
        optionalParameters.put("hl", "en");
        optionalParameters.put("google_domain", "google.com");
        
        SearchApiWebSearchEngine searchEngine = SearchApiWebSearchEngine.builder()
                .apiKey(SEARCHAPI_API_KEY)
                .engine("google")
                .optionalParameters(optionalParameters)
                .build();
        ChatLanguageModel chatModel = OpenAiChatModel.builder()
                .apiKey(OPENAI_API_KEY)
                .modelName(OpenAiChatModelName.GPT_3_5_TURBO)
                .logRequests(true)
                .build();

        WebSearchTool webTool = WebSearchTool.from(searchEngine);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatModel)
                .tools(webTool)
                .build();

        String answer = assistant.answer("My family is coming to visit me in Madrid next week, list the best tourist activities suitable for the whole family");
        System.out.println(answer);
        /*
            Here are some of the best tourist activities suitable for the whole family in Madrid:
            
            1. **Parque del Retiro** - A beautiful public park where families can enjoy nature and various activities.
            2. **Prado Museum** - A renowned art museum that can be fascinating for both adults and children.
            3. **Mercado de San Miguel** - A market where you can explore and taste delicious Spanish food.
            4. **Royal Palace** - Explore the grandeur of the Royal Palace of Madrid.
            5. **Plaza Mayor** and **Puerta del Sol** - Historic squares with a vibrant atmosphere.
            6. **Santiago Bernabeu Stadium** - Perfect for sports enthusiasts and soccer fans.
            7. **Gran Via** - A famous street for shopping, entertainment, and sightseeing.
            8. **National Archaeological Museum** - Discover Spain's rich history through archaeological artifacts.
            9. **Templo de Debod** - An ancient Egyptian temple in the heart of Madrid.
         */
    }
}
```

### Available engines in Langchain4j

| SearchApi Engine                                          | Available |
|-----------------------------------------------------------|-----------|
| [Google Web Search](https://www.searchapi.io/docs/google) | ✅         |
| [Google News](https://www.searchapi.io/docs/google-news)  | ✅         |
| [Bing](https://www.searchapi.io/docs/bing)                | ✅         |
| [Bing News](https://www.searchapi.io/docs/bing-news)      | ✅         |
| [Baidu](https://www.searchapi.io/docs/baidu)              | ✅         |

Any other engine that returns the `organic_results` array and the organic result has `title`, `link`, and `snippet` is supported by this library even if not listed above.
