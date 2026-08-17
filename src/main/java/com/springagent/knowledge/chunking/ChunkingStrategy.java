package com.springagent.knowledge.chunking;

import com.springagent.knowledge.parser.ParsedDocument;
import java.util.List;

/**
 * 分块策略：把解析后的文档切成可向量化的块。
 *
 * <p>策略由配置选择（knowledge.chunking.strategy），便于 P5 评估时
 * 对不同策略做 A/B 对比，而不是拍脑袋定参数。</p>
 */
public interface ChunkingStrategy {

    String name();

    List<Chunk> chunk(ParsedDocument document);
}
