package dev.langchain4j.data.document;

import dev.langchain4j.data.document.source.UrlSource;
import dev.langchain4j.data.document.transformer.HtmlTextExtractor;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URL;
public class WebUrlDocumentLoaderUtils {

    static Document load(UrlSource source, boolean doRemoveHtmlTags) {
        URL url = source.getUrl();
        try {
            org.jsoup.nodes.Document jsoupDocument = Jsoup.connect(url.toString()).get();
            Document document = Document.from(jsoupDocument.toString());
            source.metadata().asMap().forEach((key, value) -> document.metadata().add(key, value));
            return doRemoveHtmlTags ? new HtmlTextExtractor().transform(document) : document;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load document from URL: " + url, e);
        } catch (Exception e) {
            throw new RuntimeException("An error occurred while loading the document from URL: " + source.metadata().get("url"), e);
        }
    }
}
