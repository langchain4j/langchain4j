---
sidebar_position: 1
---

# Playwright
`BrowserExecutionEngine` represents a browser execution engine that can be used to perform actions on the browser in response to a user action.
`PlaywrightBrowserExecutionEngine` is an implementation of a `BrowserExecutionEngine` that uses <a href="https://playwright.dev/java/">Playwright Java API</a> for performing browser actions.
`BrowserUseTool` executes browser actions using `BrowserExecutionEngine`, that can be useful for **Browser-Use** Agents. You can control your browser using natural language, something like:
* `open page 'https://docs.langchain4j.dev/', and summary the page text`

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-browser-execution-engine-playwright</artifactId>
    <version>${latest version here}</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-tool-browser-use</artifactId>
    <version>${latest version here}</version>
</dependency>
```


## APIs

- `BrowserExecutionEngine`
- `PlaywrightBrowserExecutionEngine`
- `BrowserUseTool`


## Examples

```java
        Playwright playwright = Playwright.create();
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setChannel("chrome")
                .setChromiumSandbox(true)
                .setSlowMo(500);
        Browser browser = playwright.chromium().launch(options);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(BrowserUseTool.from(PlaywrightBrowserExecutionEngine.builder().browser(browser).build()))
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        String question = "open page 'https://docs.langchain4j.dev/', and summary the page text";
        System.out.println(assistant.chat(question));
```

- [PlaywrightBrowserExecutionEngineIT](https://github.com/langchain4j/langchain4j-community/blob/main/browser-execution-engines/langchain4j-community-browser-execution-engine-playwright/src/test/java/dev/langchain4j/community/browser/playwright/PlaywrightBrowserExecutionEngineIT.java)
- [BrowserUseToolIT](https://github.com/langchain4j/langchain4j-community/blob/main/tools/langchain4j-community-tool-browser-use/src/test/java/dev/langchain4j/community/tool/browseruse/BrowserUseToolIT.java)
