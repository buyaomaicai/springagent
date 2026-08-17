package com.springagent.knowledge.source;

/**
 * 知识库中的一份文档及其显式声明的元数据。
 *
 * <p>元数据全部来自注册表（sources.yml），不靠文件名推断；
 * {@code sourceUrl} 是原始出处，检索到的证据会携带它进入 evidence 闭环。</p>
 */
public record SourceDocument(
        String fileName,
        String sourceType,
        String targetVersion,
        String sourceUrl
) {
}
