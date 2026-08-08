 package com.springagent.knowledge.service;

  import java.nio.charset.StandardCharsets;
  import java.nio.file.Path;
  import java.security.MessageDigest;
  import java.util.ArrayList;
  import java.util.HashMap;
  import java.util.HexFormat;
  import java.util.List;
  import java.util.Map;
  import java.util.UUID;
  import lombok.RequiredArgsConstructor;
  import org.springframework.ai.document.Document;
  import org.springframework.ai.reader.TextReader;
  import org.springframework.ai.transformer.splitter.TokenTextSplitter;
  import org.springframework.ai.vectorstore.VectorStore;
  import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
  import org.springframework.core.io.FileSystemResource;
  import org.springframework.stereotype.Service;

  @Service
  @RequiredArgsConstructor
  public class KnowledgeIngestionService {

      private static final String SOURCE_ID =
              "spring-boot-3.0-migration-guide";

      private static final Path SOURCE_PATH = Path.of(
              "knowledge-base", "raw", "spring-boot-wiki",
              "Spring-Boot-3.0-Migration-Guide.asciidoc"
      );

      private final VectorStore vectorStore;

      public int ingestSpringBoot30Guide() {
          TextReader reader =
                  new TextReader(new FileSystemResource(SOURCE_PATH));

          reader.setCharset(StandardCharsets.UTF_8);
          reader.getCustomMetadata().putAll(Map.of(
                  "source_id", SOURCE_ID,
                  "source_type", "MIGRATION_GUIDE",
                  "source_path", SOURCE_PATH.toString(),
                  "source_url",
                  "https://github.com/spring-projects/spring-boot/wiki/"
                          + "Spring-Boot-3.0-Migration-Guide",
                  "component", "spring-boot",
                  "target_version", "3.0",
                  "language", "en"
          ));

          TokenTextSplitter splitter = TokenTextSplitter.builder()
                  .withChunkSize(500)
                  .withMinChunkSizeChars(200)
                  .withMinChunkLengthToEmbed(20)
                  .withMaxNumChunks(10_000)
                  .withKeepSeparator(true)
                  .build();

          List<Document> splitDocuments =
                  splitter.apply(reader.get());

          if (splitDocuments.isEmpty()) {
              throw new IllegalStateException("文档解析后没有有效片段");
          }

          List<Document> chunks = new ArrayList<>();
          for (int index = 0; index < splitDocuments.size(); index++) {
              chunks.add(toKnowledgeChunk(splitDocuments.get(index), index));
          }

          var filter = new FilterExpressionBuilder()
                  .eq("source_id", SOURCE_ID)
                  .build();

          vectorStore.delete(filter);
          vectorStore.add(chunks);

          return chunks.size();
      }

      private Document toKnowledgeChunk(Document source, int index) {
          String text = source.getText();

          Map<String, Object> metadata =
                  new HashMap<>(source.getMetadata());
          metadata.put("chunk_index", index);
          metadata.put("content_hash", sha256(text));

          String stableKey = SOURCE_ID + ":" + index;
          String id = UUID.nameUUIDFromBytes(
                  stableKey.getBytes(StandardCharsets.UTF_8)
          ).toString();

          return Document.builder()
                  .id(id)
                  .text(text)
                  .metadata(metadata)
                  .build();
      }

      private String sha256(String content) {
          try {
              MessageDigest digest =
                      MessageDigest.getInstance("SHA-256");
              return HexFormat.of().formatHex(
                      digest.digest(content.getBytes(StandardCharsets.UTF_8))
              );
          } catch (Exception exception) {
              throw new IllegalStateException("无法计算文档摘要", exception);
          }
      }
  }