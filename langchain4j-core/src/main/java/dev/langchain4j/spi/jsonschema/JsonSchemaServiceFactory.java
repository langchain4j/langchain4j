package dev.langchain4j.spi.jsonschema;

import static dev.langchain4j.jsonschema.JsonSchemaServiceFactories.*;

/** A factory for creating {@link Service} instances through SPI. */
public interface JsonSchemaServiceFactory {

    /** Create a new {@link Service}. */
    Service create();
}
