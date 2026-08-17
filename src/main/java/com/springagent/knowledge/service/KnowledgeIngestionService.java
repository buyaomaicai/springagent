package com.springagent.knowledge.service;

import com.springagent.knowledge.parser.DocumentParser;
import com.springagent.knowledge.parser.KnowledgeFileFormat;
import com.springagent.knowledge.parser.ParsedDocument;
import com.springagent.knowledge.source.SourceDefinition;
import com.springagent.knowledge.source.SourceDocument;
import com.springagent.knowledge.source.SourceRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

/**
 * 知识入库服务：按来源注册表遍历全部文档，解析、分块、向量化并幂等入库。
 *
 * <p>数据流：sources.yml → SourceRegistry → 按扩展名路由解析器（识别结构）→
 * 分块 → 组装元数据（来源/组件/版本/出处）→ 按 source_id 先删后插。
 * 重跑入库结果一致（幂等），且每个 chunk 的元数据可追溯回原始出处。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIngestionService {

    private static final int CHUNK_SIZE = 500;
    private static final int MIN_CHUNK_SIZE_CHARS = 200;
    private static final int MIN_CHUNK_LENGTH_TO_EMBED = 20;

    private final SourceRegistry sourceRegistry;
    private final VectorStore vectorStore;
    private final List<DocumentParser> parsers;

    /**
     * 入库注册表中的全部文档，返回入库 chunk 总数。
     */
    public int ingestAll() {
        Map<KnowledgeFileFormat, DocumentParser> parserByFormat =
                parserByFormat();
        int total = 0;
        for (SourceDefinition source : sourceRegistry.load()) {
            total += ingestSource(source, parserByFormat);
        }
        return total;
    }

    private Map<KnowledgeFileFormat, DocumentParser> parserByFormat() {
        return parsers.stream().collect(Collectors.toMap(
                DocumentParser::supportedFormat,
                parser -> parser
        ));
    }

    private int ingestSource(
            SourceDefinition source,
            Map<KnowledgeFileFormat, DocumentParser> parserByFormat
    ) {
        int count = 0;
        for (SourceDocument document : source.documents()) {
            count += ingestDocument(source, document, parserByFormat);
        }
        return count;
    }

    private int ingestDocument(
            SourceDefinition source,
            SourceDocument document,
            Map<KnowledgeFileFormat, DocumentParser> parserByFormat
    ) {
        Path file = source.rootPath().resolve(document.fileName());
        if (!Files.isRegularFile(file)) {
            log.warn("知识文档缺失，跳过: {}", file);
            return 0;
        }

        DocumentParser parser = parserByFormat.get(formatOf(file));
        if (parser == null) {
            throw new IllegalStateException(
                    "没有支持 " + formatOf(file) + " 的解析器: " + file
            );
        }

        ParsedDocument parsed = parser.parse(file, document);
        List<Document> chunks = chunk(parsed, source, document, file);

        // 幂等：先删除本来源的旧 chunk，再插入新 chunk，保证重跑结果一致。
        var filter = new FilterExpressionBuilder()
                .eq("source_id", source.id())
                .build();
        vectorStore.delete(filter);
        if (!chunks.isEmpty()) {
            vectorStore.add(chunks);
        }
        log.info(
                "知识入库完成 source={} doc={} chunks={}",
                source.id(),
                document.fileName(),
                chunks.size()
        );
        return chunks.size();
    }

    private KnowledgeFileFormat formatOf(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".asciidoc") || name.endsWith(".adoc")) {
            return KnowledgeFileFormat.ASCIIDOC;
        }
        if (name.endsWith(".md") || name.endsWith(".markdown")) {
            return KnowledgeFileFormat.MARKDOWN;
        }
        if (name.endsWith(".html") || name.endsWith(".htm")) {
            return KnowledgeFileFormat.HTML;
        }
        throw new IllegalStateException("不支持的知识文档格式: " + file);
    }

    /**
     * 把解析后的文档切块并带上完整元数据。
     *
     * <p>P0 阶段按整篇文档切块（与既有行为一致）；P1 将改为标题感知分块 +
     * 滑动窗口 overlap，届时消费 {@link ParsedDocument#sections()}。</p>
     */
    private List<Document> chunk(
            ParsedDocument parsed,
            SourceDefinition source,
            SourceDocument document,
            Path file
    ) {
        String fullText = parsed.sections().stream()
                .map(section -> section.text())
                .collect(Collectors.joining("\n\n"));

        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(CHUNK_SIZE)
                .withMinChunkSizeChars(MIN_CHUNK_SIZE_CHARS)
                .withMinChunkLengthToEmbed(MIN_CHUNK_LENGTH_TO_EMBED)
                .withMaxNumChunks(10_000)
                .withKeepSeparator(true)
                .build();
        List<Document> split = splitter.split(new Document(fullText));
        if (split.isEmpty()) {
            throw new IllegalStateException(
                    "文档解析后没有有效片段: " + file
            );
        }

        Map<String, Object> baseMetadata = new HashMap<>();
        baseMetadata.put("source_id", source.id());
        baseMetadata.put("source_type", document.sourceType());
        baseMetadata.put("source_url", document.sourceUrl());
        baseMetadata.put("component", source.component());
        baseMetadata.put("language", source.language());
        baseMetadata.put("title", parsed.title());
        baseMetadata.put("source_path", file.toString());
        if (document.targetVersion() != null) {
            baseMetadata.put("target_version", document.targetVersion());
        }

        List<Document> chunks = new ArrayList<>();
        for (int index = 0; index < split.size(); index++) {
            Document sourceChunk = split.get(index);
            String text = sourceChunk.getText();
            Map<String, Object> metadata = new HashMap<>(baseMetadata);
            metadata.put("chunk_index", index);
            metadata.put("content_hash", sha256(text));

            String stableKey = source.id()
                    + ":" + document.fileName()
                    + ":" + index;
            String id = UUID.nameUUIDFromBytes(
                    stableKey.getBytes(StandardCharsets.UTF_8)
            ).toString();

            chunks.add(Document.builder()
                    .id(id)
                    .text(text)
                    .metadata(metadata)
                    .build());
        }
        return chunks;
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(content.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算文档摘要", exception);
        }
    }
}
