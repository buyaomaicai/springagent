package com.springagent.knowledge.source;

import java.util.List;

/**
 * 知识来源注册表：回答"知识库有哪些文档、元数据是什么"。
 * 实现类从 sources.yml 加载，使"加文档 = 改配置"而不是改代码。
 */
public interface SourceRegistry {

    List<SourceDefinition> load();
}
