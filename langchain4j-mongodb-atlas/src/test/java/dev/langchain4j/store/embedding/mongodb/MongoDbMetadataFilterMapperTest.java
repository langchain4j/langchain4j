package dev.langchain4j.store.embedding.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.MongoClientSettings;
import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
import java.util.regex.Pattern;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;

class MongoDbMetadataFilterMapperTest {

    private static BsonDocument toBsonDocument(Bson bson) {
        return bson.toBsonDocument(BsonDocument.class, MongoClientSettings.getDefaultCodecRegistry());
    }

    @Test
    void should_map_contains_string_to_regex() {
        Bson bson = MongoDbMetadataFilterMapper.map(new ContainsString("name", "lang"));

        BsonDocument document = toBsonDocument(bson);
        assertThat(document.containsKey("metadata.name")).isTrue();
        assertThat(document.getRegularExpression("metadata.name").getPattern()).isEqualTo(Pattern.quote("lang"));
    }

    @Test
    void should_escape_regex_metacharacters_in_contains_string() {
        Bson bson = MongoDbMetadataFilterMapper.map(new ContainsString("name", "a.b*c"));

        BsonDocument document = toBsonDocument(bson);
        assertThat(document.getRegularExpression("metadata.name").getPattern())
                .isEqualTo(Pattern.quote("a.b*c"))
                .isEqualTo("\\Qa.b*c\\E");
    }
}
