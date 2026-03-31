# Testing Guide for Docling Document Parser

This document describes the testing strategy and setup for the Docling document parser integration.

## Test Structure

### Unit Tests (`DoclingDocumentParserTest.java`)

- **Purpose**: Test parser logic without requiring docling-serve
- **Coverage**: Input validation, constructor behavior, error handling
- **Status**: ✅ Complete (16 tests)
- **Run with**: `mvn test`

### Integration Tests (`DoclingDocumentParserIntegrationTest.java`)

- **Purpose**: Test actual document parsing with docling-serve
- **Coverage**: PDF parsing, DOCX parsing, metadata extraction, timeout handling
- **Status**: 🚧 In Progress (skeleton created, tests disabled)
- **Requires**: Running docling-serve instance

## Running Integration Tests

### Option 1: Manual docling-serve Setup

```bash
# Install docling-serve
pip install docling-serve

# Start server
docling-serve dev

# Run integration tests (after removing @Disabled annotations)
mvn test -Dtest=DoclingDocumentParserIntegrationTest
```

### Option 2: Testcontainers (Recommended - Future Enhancement)

Testcontainers will automatically start docling-serve in a Docker container during tests.

**Dependencies needed** (to be added to pom.xml):

```xml
<dependency>
    <groupId>ai.docling</groupId>
    <artifactId>docling-testcontainers</artifactId>
    <version>0.1.5</version>
    <scope>test</scope>
</dependency>
```

**Example usage**:

```java
@Testcontainers
class DoclingDocumentParserIntegrationTest {
    @Container
    static DoclingServeContainer docling = new DoclingServeContainer();

    @Test
    void shouldParsePdf() {
        DoclingDocumentParser parser = new DoclingDocumentParser(docling.getUrl());
        // ... test code
    }
}
```

**Benefits**:

- No manual server setup required
- Tests are self-contained and reproducible
- Works in CI/CD pipelines
- Automatically cleans up resources

**Status**: Planned for future implementation

## Test Resources

Sample documents for integration testing are located in `src/test/resources/`:

- Small, non-sensitive test documents
- Cover different formats: PDF, DOCX, PPTX
- Test different features: text extraction, tables, OCR

## CI/CD Considerations

- Unit tests run on every commit (fast, no dependencies)
- Integration tests require Docker for testcontainers
- Consider separate CI job for integration tests
- May need increased timeout for document processing tests

## Coverage Goals

- **Unit Tests**: 100% coverage of validation and error handling logic
- **Integration Tests**: Cover all major document formats and features
- **Edge Cases**: Large documents, corrupted files, timeout scenarios

## Future Enhancements

1. Implement testcontainers setup for automated integration testing
2. Add performance benchmarking tests
3. Add tests for concurrent parsing (thread safety)
4. Add tests with various docling-serve configuration options
5. Add sample documents to test resources directory
