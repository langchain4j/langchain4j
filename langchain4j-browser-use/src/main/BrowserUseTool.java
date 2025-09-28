package dev.langchain4j.browser.use;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrowserUseTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserUseTool.class);

    private final BrowserExecutionEngine engine;

    public BrowserUseTool() {
        this.engine = new PlaywrightBrowserExecutionEngine();
    }

    public BrowserUseTool(BrowserExecutionEngine engine) {
        this.engine = engine;
    }

    @Tool(
            name = "browser_use",
            value =
                    """
            A browser automation tool that allows interaction with a web browser to perform various actions.
            Use this when you need to browse websites, fill forms, click buttons, or extract content, etc.

            The browser_use tool supported actions: [
             - 'navigate': Go to a specific URL.
             - 'click': Click an element by a XPath / CSS selector.
             - 'reload': Reload / refresh the current page.
             - 'go_back': Navigate to the previous page in history.
             - 'go_forward': Navigate to the next page in history.
             - 'get_title': Return the page's title.
             - 'get_html': Get HTML content of the page.
             - 'get_text': Get text content of the page.
             - 'press_enter': Hit the Enter key.
             - 'wait': Wait for some seconds.
             - 'type_text': Type text into a focused element.
             - 'input_text': Input text into an element.
             - 'drag_drop': Drag the source element to the target element.
             ]

            Each action requires specific parameters as the following: {
            'navigate': ['url'],
            'click': ['element'],
            'reload': [],
            'go_back': [],
            'go_forward': [],
            'get_title': [],
            'get_html': [],
            'get_text': [],
            'press_enter': [],
            'wait': ['seconds'],
            'type_text': ['text'],
            'input_text': ['element', 'text'],
            'drag_drop': ['source', 'target'],
            }
            """)
    public String execute(
            @P("The browser action to perform.") Action action,
            @P(value = "URL for 'navigate' action", required = false) String url,
            @P(value = "Element(XPath / CSS selector) for 'click' or 'input_text' actions", required = false)
                    String element,
            @P(value = "Text for 'type_text' or 'input_text' actions", required = false) String text,
            @P(value = "Seconds to wait for 'wait' action", required = false) Integer seconds,
            @P(value = "Source element(XPath / CSS selector) for 'drag_drop' action", required = false) String source,
            @P(value = "Target element(XPath / CSS selector) for 'drag_drop' action", required = false) String target) {

        LOGGER.info("Perform action:{}", action);

        String ret = "Action '" + action + "' executed successfully";
        switch (action) {
            case navigate:
                engine.navigate(url);
                break;
            case click:
                engine.click(element);
                break;
            case reload:
                engine.reload();
                break;
            case go_back:
                engine.goBack();
                break;
            case go_forward:
                engine.goForward();
                break;
            case get_title:
                ret = engine.getTitle();
                break;
            case get_html:
                ret = engine.getHtml();
                break;
            case get_text:
                ret = engine.getText();
                break;
            case press_enter:
                engine.pressEnter();
                break;
            case wait:
                engine.waitForTimeout(seconds);
                break;
            case type_text:
                engine.typeText(text);
                break;
            case input_text:
                engine.inputText(element, text);
                break;
            case drag_drop:
                engine.dragAndDrop(source, target);
                break;
            default:
                throw new IllegalArgumentException("Unknown action: " + action);
        }
        return ret;
    }

    enum Action {
        navigate,
        click,
        reload,
        go_back,
        go_forward,
        get_title,
        get_html,
        get_text,
        press_enter,
        wait,
        type_text,
        input_text,
        drag_drop
    }
}
