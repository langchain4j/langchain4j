package dev.langchain4j.store.embedding.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.MongoClientSettings;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import java.util.Map;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;

class MongoDbMetadataFilterMapperTest {

    private static BsonDocument toBsonDocument(Bson bson) {
        return bson.toBsonDocument(BsonDocument.class, MongoClientSettings.getDefaultCodecRegistry());
    }

    @Test
    void should_map_equal_to_explicit_eq_operator() {
        Bson bson = MongoDbMetadataFilterMapper.map(new IsEqualTo("name", "lang"));

        assertThat(toBsonDocument(bson)).isEqualTo(BsonDocument.parse("{'metadata.name': {'$eq': 'lang'}}"));
    }

    @Test
    void should_not_interpret_map_value_as_operator() {
        Bson bson = MongoDbMetadataFilterMapper.map(unvalidatedIsEqualTo("tenant", Map.of("$ne", "x")));

        assertThat(toBsonDocument(bson))
                .isEqualTo(BsonDocument.parse("{'metadata.tenant': {'$eq': {'$ne': 'x'}}}"));
    }

    @Test
    void should_not_interpret_map_value_as_operator_when_negated() {
        Bson bson = MongoDbMetadataFilterMapper.map(Filter.not(unvalidatedIsEqualTo("tenant", Map.of("$eq", "a"))));

        assertThat(toBsonDocument(bson))
                .isEqualTo(BsonDocument.parse("{'metadata.tenant': {'$not': {'$eq': {'$eq': 'a'}}}}"));
    }

    // The filter classes reject values like these, so they are returned from the overridden accessor
    // to check that the store handles them safely on its own.
    private static IsEqualTo unvalidatedIsEqualTo(String key, Object value) {
        return new IsEqualTo(key, "placeholder") {
            @Override
            public Object comparisonValue() {
                return value;
            }
        };
    }
}
