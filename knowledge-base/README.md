# RAG Source Corpus

This directory holds the source registry and local raw documents used to build the RAG corpus.

- `sources.yml` records official origins and immutable Wiki commit IDs.
- `raw/` contains downloaded or exported source documents and is intentionally ignored by Git.
- `tools/fetch-official-docs.mjs` crawls only the allow-listed Oracle and Spring documentation paths.

Raw files must remain unchanged. Cleaning, section extraction, chunking, and metadata enrichment should write to a separate staging area or directly to the knowledge database. This preserves provenance and makes ingestion reproducible.

To refresh HTML sources with the local proxy on PowerShell:

```powershell
$env:HTTP_PROXY = 'http://127.0.0.1:7897'
$env:HTTPS_PROXY = 'http://127.0.0.1:7897'
$env:NO_PROXY = 'localhost,127.0.0.1'
$env:NODE_USE_ENV_PROXY = '1'
node knowledge-base/tools/fetch-official-docs.mjs
```

The crawler rejects non-HTML responses, cross-origin links, and links outside each configured guide root. Review source licensing before redistributing raw files outside the development environment.
