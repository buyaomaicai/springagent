package com.springagent.knowledge.source;

import java.nio.file.Path;
import java.util.List;

/**
 * 一个知识来源：同一来源下的文档共享组件、语言等元数据，
 * {@code id} 同时是幂等入库的删除键。
 */
public record SourceDefinition(
        String id,
        String component,
        String language,
        Path rootPath,
        List<SourceDocument> documents
) {
}
