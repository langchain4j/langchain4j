package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Period;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class JsonSchemaElementHelperTest {

    record JdkValueHolder(
            URI uri,
            URL url,
            Path path,
            Duration duration,
            Period period,
            ZoneId zoneId,
            Currency currency,
            Locale locale) {}

    @Test
    void mapsUriUrlPathDurationPeriodZoneIdCurrencyAndLocaleToJsonStringSchema() {
        JsonSchemaElement schema = JsonSchemaElementUtils.jsonSchemaElementFrom(JdkValueHolder.class);

        assertThat(schema).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema objectSchema = (JsonObjectSchema) schema;

        assertStringSchema(objectSchema, "uri", "URI");
        assertStringSchema(objectSchema, "url", "URL");
        assertStringSchema(objectSchema, "path", "Path");
        assertStringSchema(objectSchema, "duration", "Duration");
        assertStringSchema(objectSchema, "period", "Period");
        assertStringSchema(objectSchema, "zoneId", "ZoneId");
        assertStringSchema(objectSchema, "currency", "Currency");
        assertStringSchema(objectSchema, "locale", "Locale");
    }

    private static void assertStringSchema(JsonObjectSchema objectSchema, String property, String jdkType) {
        JsonSchemaElement element = objectSchema.properties().get(property);
        assertThat(element).as("%s is a JsonStringSchema", jdkType).isInstanceOf(JsonStringSchema.class);
        System.out.println(jdkType + " is a JsonStringSchema");
    }
}
