package dev.langchain4j.data.document.parser.yaml;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import java.io.InputStream;
import java.util.Objects;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Parses YAML file into a {@link Document}.
 * please refer to the <a href="https://yaml.org/">official YAML website</a>.
 */
public class YamlDocumentParser implements DocumentParser {

    @Override
    public Document parse(final InputStream inputStream) {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);

        Yaml yaml = new Yaml(loaderOptions);
        Object obj = yaml.load(inputStream);

        if (obj == null) {
            throw new BlankDocumentException();
        }

        String text = Objects.toString(obj);
        return Document.from(text);
    }
}
