## Testing

### Unit Tests (20 tests - all passing)

- Constructor validation (default, custom URL, custom timeout)
- Input validation (null, empty, valid streams)
- URL validation (HTTP, HTTPS, localhost, IP addresses, ports)
- Timeout validation (positive, zero, negative)
- Interface implementation verification
- Multiple instance creation
- Getter method verification

### Integration Tests

- Framework ready with testcontainers support
- Tests currently `@Disabled` pending docling-serve setup
- Covers PDF parsing, DOCX parsing, metadata extraction

## Documentation

- **README.md**: Installation, usage examples, troubleshooting, supported formats
- **TESTING.md**: Testing strategy, manual setup, testcontainers approach
- **CHANGELOG.md**: Complete change history
- **JavaDoc**: Comprehensive documentation for all public APIs

## Dependencies

- `docling-serve-client:0.1.5`
- `docling-serve-api:0.1.5`
- `docling-testcontainers:0.1.5` (test scope)
- `langchain4j-core:1.12.0-SNAPSHOT`

## Known Limitations

- Requires external docling-serve instance
- No OCR configuration options (future enhancement)
- No table extraction configuration (future enhancement)
- Integration tests require manual setup (testcontainers planned)

## Checklist

- [x] Code follows LangChain4j style guidelines
- [x] Comprehensive JavaDoc added
- [x] Unit tests written and passing (20 tests)
- [x] Integration test framework established
- [x] README.md updated with examples
- [x] TESTING.md created
- [x] CHANGELOG.md created
- [x] All files in correct locations
- [x] Maven build successful
- [ ] Maintainer review pending
