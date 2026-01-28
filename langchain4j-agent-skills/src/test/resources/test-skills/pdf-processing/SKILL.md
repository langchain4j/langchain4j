---
name: pdf-processing
description: Extract and analyze text from PDF documents
license: MIT
compatibility: langchain4j >= 1.0.0
allowed-tools: "*"
metadata:
  author: LangChain4j Team
  version: 1.0.0
  category: document-processing
---

# PDF Processing Skill

This skill provides capabilities to extract and analyze text content from PDF documents.

## Features

- Extract raw text from PDF files
- Analyze document structure
- Support for multi-page documents

## Usage

To extract text from a PDF file, use the extraction script:

```bash
scripts/extract.sh <pdf-file>
```

## Configuration

Configuration templates are available in the `assets/` directory:
- `config.json`: Default extraction settings
- `template.txt`: Output format template
