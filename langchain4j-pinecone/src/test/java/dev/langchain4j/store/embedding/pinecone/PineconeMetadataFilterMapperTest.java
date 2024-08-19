package dev.langchain4j.store.embedding.pinecone;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


class PineconeMetadataFilterMapperTest {

    JsonFormat.Printer printer = JsonFormat.printer().includingDefaultValueFields().omittingInsignificantWhitespace();

    @Test
    void testAtomic() throws InvalidProtocolBufferException {
        String should = "{\"key\":{\"$gt\":1.0}}";
        Filter key = new IsGreaterThan("key", 1);
        Struct map = PineconeMetadataFilterMapper.map(key);
        assertEquals(should, printer.print(map));
    }

    @Test
    void testCombineAnd() throws InvalidProtocolBufferException {
        String should = "{\"$and\":[{\"key\":{\"$gt\":1.0}},{\"key\":{\"$lt\":10.0}}]}";
        Filter filter = new IsGreaterThan("key", 1)
                .and(new IsLessThan("key", 10));
        Struct map = PineconeMetadataFilterMapper.map(filter);
        assertEquals(should, printer.print(map));
    }

    @Test
    void testCombineOr() throws InvalidProtocolBufferException {
        String should = "{\"$or\":[{\"key\":{\"$gt\":1.0}},{\"key\":{\"$lt\":10.0}}]}";
        Filter filter = new IsGreaterThan("key", 1)
                .or(new IsLessThan("key", 10));
        Struct map = PineconeMetadataFilterMapper.map(filter);
        assertEquals(should, printer.print(map));
    }

    @Test
    void testNotAtomic() throws InvalidProtocolBufferException {
        String should = "{\"key\":{\"$lte\":1.0}}";
        Filter filter = new IsGreaterThan("key", 1);
        Struct map = PineconeMetadataFilterMapper.map(Filter.not(filter));
        assertEquals(should, printer.print(map));
    }

    @Test
    void testNotCombineOr() throws InvalidProtocolBufferException {
        String should = "{\"$and\":[{\"key\":{\"$lte\":1.0}},{\"key\":{\"$gte\":10.0}}]}";
        Filter filter = new IsGreaterThan("key", 1)
                .or(new IsLessThan("key", 10));
        Struct map = PineconeMetadataFilterMapper.map(Filter.not(filter));
        assertEquals(should, printer.print(map));
    }

    @Test
    void testNotCombineAnd() throws InvalidProtocolBufferException {
        String should = "{\"$or\":[{\"key\":{\"$lte\":1.0}},{\"key\":{\"$gte\":10.0}}]}";
        Filter filter = new IsGreaterThan("key", 1)
                .and(new IsLessThan("key", 10));
        Struct map = PineconeMetadataFilterMapper.map(Filter.not(filter));
        assertEquals(should, printer.print(map));
    }

}