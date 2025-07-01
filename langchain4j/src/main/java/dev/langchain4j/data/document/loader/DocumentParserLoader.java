package dev.langchain4j.data.document.loader;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.Internal;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.spi.data.document.parser.DocumentParserFactory;

@Internal
class DocumentParserLoader {
    static DocumentParser loadDocumentParser() {
        var factories = loadFactories(DocumentParserFactory.class);

        if (factories.size() > 1) {
            throw new RuntimeException("Conflict: multiple document parsers have been found in the classpath. "
                    + "Please explicitly specify the one you wish to use.");
        }

        for (DocumentParserFactory factory : factories) {
            return factory.create();
        }

        return null;
    }
}
