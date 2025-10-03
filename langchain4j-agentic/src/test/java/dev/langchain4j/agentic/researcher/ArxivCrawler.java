package dev.langchain4j.agentic.researcher;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.concurrent.atomic.AtomicInteger;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArxivCrawler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArxivCrawler.class);

    AtomicInteger counter = new AtomicInteger(0);

    @Tool("Search the most relevant scientific paper on the given topic and return its title, summary, link, and full content.")
    public ArxivSearchResult search(@P("topic") String topic) {
        String webUrl = "http://export.arxiv.org/api/query?search_query=ti:\\\"" + topic + "\\\"&sortBy=relevance&start=" + counter.getAndIncrement() + "&max_results=1";
        try {
            LOGGER.info("Querying Arxiv: " + webUrl);
            Document doc = Jsoup.connect(webUrl).get();
            Element entry = doc.getElementsByTag("entry").get(0);
            return entryToSearchResult(entry);
        } catch (Exception e) {
            return search(topic);
        }
    }

    private static ArxivSearchResult entryToSearchResult(Element entry) {
        String title = entry.getElementsByTag("title").get(0).text();
        String summary = entry.getElementsByTag("summary").get(0).text();
        String link = entry.getElementsByTag("id").get(0).text().replace("http:", "https:").replace("abs", "pdf");
        LOGGER.info("Downloading " + title + " from " + link);
        String content = parse(link);
        return new ArxivSearchResult(title, summary, link, content);
    }

    private static String parse(String pdfUrl) {
        try {
            return parseBytes(downloadUrl(pdfUrl));
        } catch (Exception e) {
            LOGGER.error("Could not download or parse the PDF from " + pdfUrl + ", retrying...");
            throw new RuntimeException("Could not download or parse the PDF from " + pdfUrl, e);
        }
    }

    private static byte[] downloadUrl(String pdfUrl) throws URISyntaxException, IOException {
        URLConnection urlConn = new URI(pdfUrl).toURL().openConnection();
        try (BufferedInputStream in = new BufferedInputStream(urlConn.getInputStream())) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte dataBuffer[] = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 4096)) != -1) {
                byteArrayOutputStream.write(dataBuffer, 0, bytesRead);
            }
            return byteArrayOutputStream.toByteArray();
        }
    }

    private static String parseBytes(byte[] bytes) throws IOException {
        PDDocument document = Loader.loadPDF(bytes);
        PDFTextStripper pdfStripper = new PDFTextStripper();
        return pdfStripper.getText(document);
    }
}
