package com.springagent.knowledge.parser;

import com.springagent.knowledge.source.SourceDocument;
import java.nio.file.Path;

/**
 * 知识文档解析器：把 raw 文件解析为带结构的 ParsedDocument。
 *
 * <p>新增格式（如 PDF/OCR）只需新增实现类并按格式路由，调用方不变。</p>
 */
public interface DocumentParser {

    KnowledgeFileFormat supportedFormat();

    ParsedDocument parse(Path file, SourceDocument definition);
}
