package dev.langchain4j.agentic.patterns.goap.horoscope;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.io.IOException;
import org.jsoup.Jsoup;

public class WebSearchTool {

    @Tool("Perform a web search to retrieve information online with a full text response")
    String webSearch(@P("search") String search) throws IOException {
        String webUrl = "https://html.duckduckgo.com/html/?q=" + search;
        return Jsoup.connect(webUrl).get().text();
    }

    @Tool("Perform a web search to retrieve information online with a short response")
    String shortWebSearch(@P("search") String search) throws IOException {
        return webSearch(search).substring(0, 500);
    }
}
