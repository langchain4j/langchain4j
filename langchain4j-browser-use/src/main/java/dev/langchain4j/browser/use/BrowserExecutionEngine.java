package dev.langchain4j.browser.use;

public interface BrowserExecutionEngine {
    void navigate(String url);

    void click(String element);

    void reload();

    void goBack();

    void goForward();

    String getTitle();

    String getHtml();

    String getText();

    void pressEnter();

    void waitForTimeout(Integer seconds);

    void typeText(String text);

    void inputText(String element, String text);

    void dragAndDrop(String source, String target);

    void close();
}
