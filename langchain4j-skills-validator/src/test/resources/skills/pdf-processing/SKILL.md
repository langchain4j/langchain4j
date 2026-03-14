---
name: pdf-processing
description: Extracts text and tables from PDF files, fills PDF forms, and merges multiple PDFs. Use when working with PDF documents or when the user mentions PDFs, forms, or document extraction.
license: Apache-2.0
compatibility: Requires Python 3.8+ and PyPDF2 library
metadata:
  author: langchain4j-team
  version: "1.0"
allowed-tools: Bash(python:*) Read Write
---

# PDF Processing Skill

This skill provides comprehensive PDF processing capabilities.

## Features

- Extract text and tables from PDF files
- Fill PDF forms programmatically
- Merge multiple PDF documents
- Split PDF pages
- Convert PDF to images

## Usage

Use this skill when you need to:
- Extract information from PDF documents
- Automate PDF form filling
- Combine multiple PDFs into one
- Process large batches of PDF files

## Requirements

- Python 3.8 or higher
- PyPDF2 library
- pdfplumber for table extraction

## Examples

### Extract Text
```python
from pypdf2 import PdfReader
reader = PdfReader("document.pdf")
text = reader.pages[0].extract_text()
```

### Merge PDFs
```python
from pypdf2 import PdfMerger
merger = PdfMerger()
merger.append("file1.pdf")
merger.append("file2.pdf")
merger.write("merged.pdf")
```
