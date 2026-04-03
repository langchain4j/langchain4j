# Changelog

All notable changes to the Docling Document Parser module will be documented in this file.

## [Unreleased]

### Added

- Initial implementation of DoclingDocumentParser
- DocumentParser interface implementation for Docling integration
- Configurable timeout support with validation
- Comprehensive JavaDoc documentation for all public APIs
- Metadata extraction (processing time, document size, error tracking, timeout)
- Input validation for null and empty streams
- Server URL validation
- Base64 document encoding for API transmission
- Error handling with detailed exception messages
- 19 unit tests covering all validation logic
- Integration test skeleton with @Disabled tests
- TestDocumentHelper utility for test document generation
- TESTING.md documentation for testing strategy
- Enhanced README with usage examples and troubleshooting guide
- Test resources directory structure

### Configuration

- Default server URL: http://localhost:5001
- Default timeout: 60 seconds
- Configurable server URL via constructor
- Configurable timeout via constructor

### Dependencies

- docling-serve-client 0.1.5
- docling-serve-api 0.1.5
- docling-testcontainers 0.1.5 (test scope)
- langchain4j-core 1.12.0-SNAPSHOT

### Supported Features

- PDF document parsing
- DOCX document parsing
- Markdown output format
- REST API communication with docling-serve
- Comprehensive error reporting
- Processing time tracking

### Known Limitations

- Requires external docling-serve instance
- No OCR configuration options yet
- No table extraction configuration options yet
- Integration tests require manual setup

## Future Enhancements

- Testcontainers integration for automated testing
- OCR enable/disable configuration
- Table extraction configuration options
- Additional metadata extraction (page count, format detection)
- Performance benchmarking utilities
