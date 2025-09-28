package dev.langchain4j.browser.use;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import io.orangebuffalo.testcontainers.playwright.PlaywrightContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class PlaywrightBrowserExecutionEngineIT {

    static PlaywrightContainer playwrightContainer;
    static Browser browser;

    static PlaywrightBrowserExecutionEngine engine;

    @BeforeAll
    static void beforeAll() {
        playwrightContainer = new PlaywrightContainer();
        playwrightContainer.start();
        browser = playwrightContainer.getPlaywrightApi().chromium();
        engine = new PlaywrightBrowserExecutionEngine(browser);
    }

    @AfterAll
    static void afterAll() {
        if (browser != null) {
            browser.close();
        }
        if (playwrightContainer != null) {
            playwrightContainer.stop();
        }
    }

    @Test
    void should_exec_actions() {
        engine.navigate("https://docs.langchain4j.dev/");

        assertThat(engine.getTitle()).contains("LangChain4j");
        assertThat(engine.getHtml()).contains("Java");
        assertThat(engine.getText()).contains("LLM");
    }
}
