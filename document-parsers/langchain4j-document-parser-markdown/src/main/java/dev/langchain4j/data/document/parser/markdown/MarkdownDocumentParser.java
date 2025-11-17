package dev.langchain4j.data.document.parser.markdown;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;

/**
 * Parses Markdown file into a {@link Document}.
 * please refer to the <a href="https://www.markdownguide.org/">official Markdown website</a>.
 */
public class MarkdownDocumentParser implements DocumentParser {

    @Override
    public Document parse(final InputStream inputStream) {
        try {
            Parser parser = Parser.builder().build();
            final Node node = parser.parseReader(new InputStreamReader(inputStream));

            // Renders nodes to plain text
            final TextContentRenderer renderer = TextContentRenderer.builder().build();
            final String text = renderer.render(node);

            if (isNullOrBlank(text)) {
                throw new BlankDocumentException();
            }

            return Document.from(text);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
