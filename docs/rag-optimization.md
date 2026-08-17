# RAG 优化闭环设计

> 目的：把 RAG 面试高频考点落地为 SpringShift 的真实工程能力，形成"数据 → 预处理 → 索引 →
> 检索 → 查询优化 → 生成 → 评估 → 调参回流"的可演示闭环。每一阶段都是一次可验证的增量，
> 附带面试讲点（30 秒话术）。

## 一、现状盘点（2026-08）

| 环节 | 现状 | 差距 |
| --- | --- | --- |
| 知识源 | `knowledge-base/sources.yml` 注册 4 类来源；`raw/` 已有 42 份官方文档 | 只入库 1 份（Spring Boot 3.0 迁移指南），其余 41 份未入库 |
| 文档解析 | `TextReader` 直接读纯文本 | 无格式区分；HTML 未转 Markdown；表格/结构信息丢失 |
| 分块 | `TokenTextSplitter` chunk=500 | 无 overlap（滑动窗口）；策略硬编码，不可配置、不可对比 |
| 元数据 | source_id/source_type/source_url/component/target_version 等 | 覆盖完整，但只服务于单文档；缺章节标题等结构信息 |
| 向量索引 | pgvector 1024 维、HNSW、COSINE | 已有（性能叙事可用） |
| 关键词索引 | 无 | 无 FTS（tsvector），无法做混合检索 |
| 检索 | 单通道向量检索，topK=5，阈值 0.55，filter 硬编码 `spring-boot 3.0` | 无混合检索/RRF；无阈值降级；无 Query Rewrite/HyDE；无 Rerank |
| Prompt | references 带编号引用（[REF-i]） | 无 Lost-in-the-Middle 布局优化；Faithfulness 约束可强化 |
| 评估 | RagInfrastructureTests 冒烟 | 无 Recall@K/MRR；无 RAGAS；无评估数据集 |

## 二、考点 → 改造点映射

| 面试考点 | 改造点 | 落地阶段 |
| --- | --- | --- |
| PDF 复杂表格/流程图怎么处理 | 本地文档为 asciidoc/md/html：HTML 表格 → Markdown 表格转换器（保留结构）；流程图说明：视觉信息丢失是痛点，工程上可接 VLM 摘要（设计留接口，不硬接本地无 GPU 的 OCR） | P0 解析篇 |
| 分块策略有哪些；滑动窗口(Overlap)作用 | ChunkingStrategy 策略化（固定/段落/标题层级）；overlap 可配置；测试证明"overlap 避免关键实体被切断" | P1 分块篇 |
| 如何选 Embedding 模型；BGE vs Ada | Embedding 模型可配置（默认 bge-m3）；在自有数据集上做 Recall@K 验证，不迷信 MTEB | P2/P5 评估篇 |
| 向量 vs BM25 优缺点；混合检索 + RRF | PostgreSQL FTS（tsvector+GIN+ts_rank）关键词通道 + pgvector 向量通道 + RRF 融合 | P2 检索篇 |
| Lost in the Middle | Prompt 引用布局优化：高分证据放开头与结尾，中间放次要内容 | P4 生成篇 |
| Top-K 都不相关怎么办 | 检索置信度阈值 → "未找到相关知识"状态传入 prompt；触发 Query Rewrite 二次检索；分数可观测 | P2/P3 |
| 复杂查询（去年 vs 前年）| QueryRewriteService：LLM 多步拆解（时间计算/指标提取/分别检索） | P3 查询篇 |
| HyDE 原理 | HydeRetrievalStrategy：先 LLM 生成假设答案文档，再向量检索；可配置开关做 A/B | P3 查询篇 |
| 防止模型忽视文档靠参数幻觉 | system.st 强约束"仅依据参考文档，未找到必须明说，禁止编造"；后端 Faithfulness 评估兜底 | P4/P5 |
| 响应太慢怎么优化 | HNSW（已有）+ SSE 流式（已有）；Rerank 减少送 LLM 文档数（bge-reranker，可选）；Redis 缓存（设计文档） | P6 性能篇 |
| 怎么评估 RAG 系统 | 检索侧 Recall@K / MRR（Java 端评估模块 + 评估数据集）；生成侧 RAGAS 三指标（Faithfulness/Answer Relevancy/Context Relevancy，LLM-as-judge 简化实现） | P5 评估篇 |

## 三、目标架构

```mermaid
flowchart LR
    S[知识源 sources.yml] --> P[多格式解析<br/>asciidoc/md/html→Markdown<br/>表格保留]
    P --> C[分块 + 滑动窗口 Overlap]
    C --> M[元数据富化<br/>来源/组件/版本/章节]
    M --> V[(pgvector 向量索引)]
    M --> F[(PostgreSQL FTS<br/>tsvector + GIN)]
    Q[用户问题] --> RW[Query Rewrite / HyDE]
    RW --> H[混合检索<br/>向量 + 关键词 + RRF]
    H --> T[阈值与降级<br/>置信度不足→未找到/二次检索]
    T --> PB[Prompt 组装<br/>Lost-in-the-Middle 布局]
    PB --> G[生成<br/>Faithfulness 强约束]
    G --> E[评估<br/>Recall@K / MRR / RAGAS]
    E -.调参回流.-> C
```

## 四、分阶段实施

### P0 数据地基：多文档入库（解析篇）
- 由 `sources.yml` 驱动的批量入库；按扩展名路由解析器（asciidoc / markdown / html）
- HTML → Markdown 转换（jsoup），**表格转为 Markdown 表格**保留结构
- 幂等入库：按 source_id 先删后插；完整元数据（来源、组件、适用版本、语言）
- 验收：42 份文档全部入库；集成测试验证"检索 JDK 17 迁移问题能命中对应文档"
- 面试讲点：来源可追溯（provenance）、幂等重建、多格式解析、表格结构保留

### P1 预处理参数化：分块与滑动窗口（分块篇）
- `ChunkingStrategy` 接口 + 三种实现（固定大小 / 按段落 / 按标题层级）
- overlap 滑动窗口参数化；chunk_size 与 overlap 写入 application.yml
- 单元测试演示：构造一段在 chunk 边界被切断的文本，证明 overlap 后实体完整
- 面试讲点：RecursiveCharacterTextSplitter 思想、overlap 作用、chunk size 与 embedding max tokens 的关系、参数要基于评估调不是拍脑袋

---

## P1 代码级设计（分块策略与滑动窗口）

### 为什么分块是关键环节
- Embedding 模型有 max tokens（bge-m3 为 8192），但**超长文本向量化会稀释语义**——平均池化后关键实体被淹没
- chunk 太小 → 语义不完整；太大 → 语义模糊、检索噪音
- 经典经验区间 300–800 tokens，**最终取值必须靠评估，不能拍脑袋**

### 策略三选项（对照 LangChain RecursiveCharacterTextSplitter）
| 策略 | 优点 | 缺点 |
| --- | --- | --- |
| 固定大小 | 简单、块大小均匀 | 可能从句子中间切开，语义破碎 |
| 按段落/句子 | 语义完整 | 块大小不均，长段落超限 |
| 按标题层级（结构化） | 保留章节上下文 | 依赖文档有清晰标题 |

- **RecursiveCharacterTextSplitter 思想**：按分隔符优先级递归切分（`\n\n` → `\n` → 句号 → 空格），高优先级分隔符不可用时降级——我们可借鉴
- 我们的文档是 asciidoc/md/html，**都有标题结构** → 适合"标题感知"分块

### 滑动窗口（overlap）
- 解决的问题：关键实体恰好在切分边界被切断（"Spring Boot 3.0 迁移指南" → "Spring Boot 3.0 迁" + "移指南"，两边都搜不到）
- 方案：相邻 chunk 共享重叠文本（固定 token 数或百分比）
- 代价：索引体积膨胀 (1+overlap%)、检索可能返回重复内容
- 设计：overlap 可配置，默认推荐固定 50 tokens 或 10%

### 进阶：标题注入（Heading Injection）
- 把章节标题作为每个 chunk 的前缀 → chunk 自带"我在讲哪个主题"的上下文
- 检索"Spring Boot 3.0 配置变化"时，能命中属于配置章节的 chunk
- 对证据链路无副作用：evidence 的 excerpt 会带上标题，展示时反而更有上下文

### 类设计
```
ChunkingStrategy                       # 接口：chunk(ParsedDocument) → List<Chunk>
  ├─ FixedSizeChunkingStrategy         # chunkSize + overlap，滑动窗口
  ├─ ParagraphChunkingStrategy         # 按段落聚合到目标大小
  └─ HeadingAwareChunkingStrategy      # 按标题切 + 标题注入 + overlap
Chunk                                  # record：index / text / metadata(标题、章节路径)
配置：knowledge.chunking.strategy / size / overlap（application.yml）
```

### 测试设计（用测试证明设计决策）
1. **边界切割演示**：构造文本使关键短语落在切分点 → 断言无 overlap 时检索不到、有 overlap 时能召回（纯单测，不需要 DB）
2. **标题注入**：断言 chunk 文本以章节标题开头
3. **策略纯函数单测**：每种策略给定输入 → 断言 chunk 数量与内容

### 面试话术
> "分块我做成可配置策略，默认标题感知 + 滑动窗口 overlap；我专门写了一个测试：把关键实体放在切分边界，没有 overlap 时检索不到，加了 overlap 就能召回——参数不是拍脑袋，是评估驱动的。"


---

## P0 代码级设计（多格式解析与多文档入库）

### 设计问题清单
1. 一份文档从 `raw/` 到向量库要经过哪些步骤？→ **解析 → 清洗 → 分块 → 元数据富化 → 向量化 → 入库**，每一步可独立测试
2. 42 份文档三种格式（asciidoc/md/html）怎么统一？→ **解析器按扩展名路由**（策略模式）
3. 重跑入库会不会重复？→ **幂等**：按 `source_id` 先删后插
4. 检索时怎么知道文档属于哪个组件/版本？→ **元数据契约**（检索 filter 的键）

### 类设计
```
sources.yml
  └─ SourceRegistry            # 解析注册表 → List<SourceDefinition(id, type, rootPath, documents)>
       └─ SourceDefinition     # 单个来源：id、类型(git/web)、本地根目录、文档清单
DocumentParser                 # 接口：parse(Path) → ParsedDocument(text, title, metadata)
  ├─ AsciidocDocumentParser    # 文本流 + 标题提取（= 现有 TextReader 的封装）
  ├─ MarkdownDocumentParser    # 文本流 + 标题层级提取
  └─ HtmlDocumentParser        # jsoup 选正文 → 转 Markdown（表格保留为 | 列 | 列 |）
KnowledgeIngestionService      # 重构：ingestAll() 遍历 registry，按扩展名路由 parser
```

### 元数据契约（字段 → 用途）
| 字段 | 用途 |
| --- | --- |
| `source_id` | 幂等删除键 + 可追溯 |
| `source_type` | 检索来源过滤（对应 knowledge_evidence 的 CHECK 枚举） |
| `component` / `target_version` | 检索 filterExpression 的键（现有 searchSpringBoot30 就靠它过滤） |
| `language` | 中/英文文档区分 |
| `chunk_index` / `content_hash` | 可复现、可审计 |

### 依赖取舍（待讨论）
- HTML→Markdown 需要 jsoup（新增依赖）或 Hutool 的有限 HTML 工具或手写正则——**选型讨论点**

### 测试设计
- 每个 parser 的单元测试（HTML 表格 → Markdown 表格断言、标题提取断言）
- SourceRegistry 解析 `sources.yml` 测试
- 入库幂等（集成测试，需 PostgreSQL+Ollama）
- 检索命中（集成测试：问 JDK 17 迁移问题 → 命中 jdk-17-migration 文档）

### P2 混合检索：向量 + 关键词 + RRF（检索篇）
- DDL：知识表加 `tsvector` 列 + 触发器自动维护 + GIN 索引
- 双通道：pgvector 相似度 + `ts_rank` 关键词打分
- RRF 融合（`score = Σ 1/(k + rank)`，k 常取 60），返回带通道来源的结果
- 置信度阈值：低于阈值标记"未找到相关知识"，供 prompt 与降级逻辑使用
- 面试讲点：向量 vs BM25 的取舍（专有名词漏召回 vs 同义词不识别）、RRF 公式、阈值容错

---

## P2 代码级设计（双通道索引 + 混合检索 + RRF + 阈值容错）

### 为什么必须双通道（互补性）
| 通道 | 强 | 弱 |
| --- | --- | --- |
| 向量（语义） | 同义词、口语化问题、模糊表达 | 漏专有名词（`javax.servlet`、`GB/T 12345`） |
| 关键词（ts_rank/BM25） | 精确字符串命中 | 不懂同义词（"升级" vs "迁移"） |

结论：**融合，不是二选一**。

### 存储设计：Postgres 双通道，不加中间件
- 向量通道：pgvector（已有：1024 维、HNSW、COSINE）
- 关键词通道：**中文用 pg_trgm 三元组索引（similarity/ILIKE），英文用 tsvector FTS**——
  Postgres 默认分词对中文无效（无空格分词），详见"设计评审发现·缺陷 3"
- **架构决策（待讨论）**：扩展 Spring AI 默认 `vector_store` 表（加关键词列与索引），还是自建知识表？倾向扩展 Spring AI 表——能继续用 VectorStore 抽象做相似度检索，关键词走原生 SQL，服务层融合。零新中间件是面试加分点

### RRF 融合（为什么用排名不用分数）
- 公式：`score(d) = Σ 1/(k + rank_i(d))`，k 常取 60
- 理由：两通道分数分布不同（余弦 0~1 vs ts_rank 无界），归一化相加需要调权重；RRF 只看排名，**无需调参、天然鲁棒**
- 手算例子：文档在向量通道第 1、关键词通道第 3 → 融合分 = 1/61 + 1/63

### 阈值与容错（Top-K 都不相关怎么办）
- 融合分低于阈值 → 检索结果标记 `UNFOUND` 状态
- 三级容错：① prompt 告知模型"未找到相关知识，请明说"；② 触发 Query Rewrite 二次检索；③ 放宽阈值/扩大 topK 的降级策略
- 面试讲点：**系统知道自己不知道**，Fail-gracefully，不硬编答案

### 类设计
```
RetrievalStrategy                # 接口：retrieve(SearchRequest) → List<RetrievedEvidence>
  ├─ VectorRetrievalStrategy     # pgvector 通道
  ├─ KeywordRetrievalStrategy    # tsvector + ts_rank 通道
  └─ HybridRetrievalStrategy     # 双通道 + RRF + 阈值
SearchRequest(query, topK, threshold, filters)
RetrievedEvidence(document, score, channel)   # channel 来源可观测（VECTOR/KEYWORD/HYBRID）
KnowledgeRetrievalService        # 重构：硬编码 searchSpringBoot30 → 通用 search(SearchRequest)
```

### 测试设计
1. **RRF 融合单测**：给定两个通道的排名 → 断言融合分与排序（公式可手算）
2. **阈值降级**：低分查询 → 返回 UNFOUND 状态，不硬凑结果
3. **混合检索价值证明（集成测试）**：专有名词查询（如 `javax.servlet`）→ 纯向量漏召回、混合检索命中——这是混合检索必要性的核心证据

### 面试话术
> "检索我做了双通道：pgvector 向量 + Postgres 自带 FTS 关键词，RRF 融合排名而不是融合分数——两路分数分布不同，RRF 只看排名天然鲁棒。低于阈值我明确返回'未找到'而不是硬答。我用一个专有名词的用例证明了纯向量会漏召回、混合检索能命中。"


### P3 查询理解：Query Rewrite + HyDE（查询篇）
- `QueryRewriteService`：DeepSeek 将原始问题重写为利于检索的形式（多轮历史 + 目标版本感知）
- `HydeRetrievalStrategy`：先生成假设答案文档再检索（可配置开关）
- 面试讲点：复杂查询拆解（时间计算/指标提取/分别检索）、HyDE 原理（短问题语义缺失的补偿）与代价（多一次 LLM 调用 + 延迟）

---

## P3 代码级设计（Query Rewrite + HyDE）

### 问题本质
用户问题往往"短、口语、含指代"（"它怎么办""去年和前年对比"），直接拿原文检索效果差。两个层次的处理：
1. **Query Rewrite**：LLM 把问题改写/拆解为利于检索的形式——补全时间（去年=2025）、提取关键指标、拆成子查询分别检索
2. **HyDE（进阶）**：先让 LLM 生成一段"假设答案文档"，再拿假设答案去检索——弥补短问题缺失的语义，假设答案比问题更接近目标文档的表述方式

### 代价分析（面试讲深度）
- Query Rewrite：多一次 LLM 调用（延迟 + 成本），必须做成**可配置开关**，用 P5 评估证明"重写后 Recall@K 提升了"
- HyDE：代价更高——假设答案可能是错的（幻觉），方向错了检索也偏。**高风险高收益**，适合问题特别短/口语化严重的场景，不适合精确查询
- 设计决策：两个都是**可插拔的查询预处理策略**，默认关闭或轻量启用，靠评估数据说话

### 类设计
```
QueryPreprocessor                # 接口：preprocess(QueryContext) → List<String> 检索查询
  ├─ IdentityPreprocessor        # 原样返回（基线）
  ├─ RewritePreprocessor         # DeepSeek 重写 + 可选拆解为多个子查询
  └─ HydePreprocessor            # 生成假设答案 → 用假设答案检索
QueryContext(question, history, targetVersion)
RetrievalPipeline                # 编排：preprocess → 混合检索 → 阈值 → 结果
```

### 测试设计
1. **重写规则单测**：历史多轮中的"它"被正确替换为实体（可先做规则版/用 LLM mock）
2. **管线编排测试**：Identity vs Rewrite 走同一检索接口
3. **集成评估（P5 打通）**：同一批问题，对比"是否重写"的 Recall@K——用数据决定开关

### 面试话术
> "查询侧我做了可插拔的预处理：Query Rewrite 用 LLM 补全指代和拆解复杂问题；HyDE 先生成假设答案再检索，弥补短问题的语义缺失。但两个都有代价（多一次 LLM 调用、HyDE 可能被幻觉带偏），所以我做成开关，用 Recall@K 评估决定开不开——不靠感觉，靠数据。"

---

## P4 代码级设计（Lost in the Middle + Faithfulness）

### Lost in the Middle（OpenAI 论文）
模型对长上下文**开头和结尾关注度高、中间易忽略**。缓解：最相关的 Top-3 证据放 prompt 首尾，次要放中间；或压缩不相关内容。
- 实现位置：`DiagnosisPromptBuilder`——检索结果按分数排序后，按"首尾放高分"布局重组

### Faithfulness 双防线
1. **Prompt 强约束**（system.st）："仅依据参考文档回答；未找到明确信息必须明示'未找到相关信息'；严禁编造"——第一道
2. **后端评估兜底**（P5）：Faithfulness 打分，答案与文档不一致就告警——第二道
- **设计哲学：不把可靠性押在 prompt 上**（模型可能不遵守），必须有后端验证

### 类设计
```
EvidenceLayoutStrategy           # 接口：layout(List<RetrievedEvidence>) → 重排后的引用
  ├─ OriginalOrderLayout         # 按分数降序（基线）
  └─ SandwichLayout              # 高分放首尾（Lost in the Middle 缓解）
system.st                        # 强化"仅依据文档、未找到必须明示、禁止编造"
```

### 测试设计
1. **布局单测**：给定分数序列 → 断言首尾是高分区（SandwichLayout 行为）
2. **prompt 内容断言**：system 文本包含"未找到相关信息"等强约束句

### 面试话术
> "生成侧我做了两道防线：prompt 强约束'仅依据文档、未找到必须明说'，另外用 Sandwich 布局把高分证据放首尾，缓解 Lost in the Middle。但我知道 prompt 不可靠，所以后端还有 Faithfulness 评估兜底——可靠性不能押在提示词上。"

---

## P5 代码级设计（评估体系：Recall@K / MRR / RAGAS）

### 为什么评估是闭环的最后一环（也是第一环）
没有评估，前面的所有优化都是"我觉得变好了"。评估结果**回流调参**（分块大小、阈值、是否重写、是否 HyDE），形成真正的闭环。

### 指标（检索侧与生成侧分开）
| 指标 | 公式/含义 | 测什么 |
| --- | --- | --- |
| Recall@K | 前 K 个结果中命中的期望文档数 / 期望文档总数 | 搜没搜到 |
| MRR | 第一个命中文档排名的倒数均值 | 排序对不对 |
| Faithfulness | 答案有多少内容能被上下文支撑（LLM-as-judge） | 有没有幻觉 |
| Answer Relevancy | 答案与问题切题程度 | 答没答到点上 |
| Context Relevancy | 上下文冗余度 | 检索结果干不干净 |

### 评估数据集
```json
{ "queries": [
  { "query": "Spring Boot 3.0 如何从 javax 迁移到 jakarta？",
    "expected_doc_ids": ["spring-boot-3.0-migration-guide"],
    "ground_truth": "……标准答案……" }
]}
```
- expected_doc_ids → 检索侧指标（不需要 LLM）
- ground_truth → 生成侧指标（LLM-as-judge）

### 类设计
```
EvaluationDataset                # 加载 JSON 评估集
RetrievalEvaluator               # 跑检索 → 算 Recall@K / MRR → 输出报告
FaithfulnessEvaluator            # LLM-as-judge：答案 vs 上下文 → 0~1 分
EvaluationReport                 # 汇总：按查询粒度 + 全局指标
```

### 面试话术
> "评估我分成检索侧和生成侧：检索侧用 Recall@K 和 MRR（只需要标注期望命中文档，不依赖 LLM）；生成侧用 RAGAS 的 Faithfulness/Answer Relevancy/Context Relevancy，用 LLM-as-judge 实现。Embedding 模型、分块参数、检索阈值都不是拍脑袋定的——我在自己的评估集上跑指标，用数据说话。"

---

## P6 性能与可选增强（性能篇，简述）
- Rerank：bge-reranker 本地模型，Top-20 → Rerank → Top-5，减少送 LLM 的文档数（成本/延迟优化）
- Redis 高频问题缓存：设计文档级，本地可不装
- 已有资产：HNSW 索引、SSE 流式输出——面试可直接讲

## 五、验收指标

| 维度 | 指标 | 验证方式 |
| --- | --- | --- |
| 数据覆盖 | 入库文档数、来源数 | 集成测试断言 + 查询计数 |
| 检索质量 | Recall@K、MRR | P5 评估模块输出 |
| 生成质量 | Faithfulness 得分 | LLM-as-judge 报告 |
| 工程性 | 配置化、可测试、可演示 | mvn test + 手动 curl 演示 |

## 六、风险与说明

- RAG 集成测试依赖 PostgreSQL + Ollama（bge-m3），本地需先启动基础设施
- 本地无 GPU，OCR/VLM 方案只做接口预留与设计说明，不硬接
- Rerank 依赖 Ollama 是否有 bge-reranker 模型，作为可选项
- 每次改动保持"先讲原理 → 再实现 → 再验证 → 总结面试话术"的学习节奏

---

## 七、设计评审发现（自审，2026-08）

> 对 P0–P2 设计做了一次"评审者视角"复查，发现 3 个会直接影响实现的设计缺陷。
> 评审方法：对每个环节追问三个问题——"数据从哪来？数据怎么用？坏了怎么发现？"

### 缺陷 1：元数据没有"来源"，入库时编不出来（P0）

**问题**：`sources.yml` 只注册了来源和文档路径，但 `component`/`target_version`/`language`/
`source_type` 这些检索必需字段**没有声明**。如果入库时靠文件名猜（`jdk-17-migration` → jdk/17），
规则脆弱且不可扩展。

**影响**：P2 检索的 filter（`component == 'spring-boot'`）会失效；P0 幂等键之外的元数据不可靠。

**修正**：`sources.yml` 为每个来源声明显式元数据（component、language），文档级覆盖可选；
入库时以注册表为准，解析器只补充结构性元数据（标题、chunk 序号）。

**面试价值**："元数据要显式声明，不能靠文件名推断"——数据契约思维。

### 缺陷 2：ParsedDocument 没有结构，P1 标题感知分块做不了（P0→P1 衔接）

**问题**：P0 设计的 `DocumentParser.parse()` 返回 `(text, title, metadata)`，但 P1 的
"标题感知分块 + 标题注入"需要**章节层级**（哪段文本属于哪个标题下）。

**影响**：P1 无法实现；标题注入（面试加分点）落空。

**修正**：`ParsedDocument` 增加**章节结构**（有序的 `Section(title, level, text)` 列表），
解析器负责按标题切分，分块器消费结构而非裸文本。解析和分块的责任边界因此更清晰：
**解析负责"识别结构"，分块负责"切与合并"**。

### 缺陷 3（关键）：Postgres 默认 FTS 对中文无效（P2）

**问题**：`to_tsvector('simple', content)` 按空格/标点分词。**中文没有空格**，
一段中文会变成"一整块 token"，`ts_rank` 打分失效，关键词通道对中文查询等于没有。
本项目知识库以中文查询为主（诊断输入是中文），这是致命缺陷。

**影响**：P2 关键词通道形同虚设；混合检索退化为纯向量。

**修正方案**（按成本排序）：
1. **pg_trgm（内置扩展，推荐）**：三元组索引 + `similarity()`/`%` 操作符，支持中文子串
   匹配，零新依赖（`CREATE EXTENSION pg_trgm`）——"Postgres FTS 默认分词对中文无效，
   我改用 pg_trgm 三元组做中文子串召回"是极好的面试素材
2. pg_bigm（外部扩展，CJK 大二元组，效果更好但需编译安装）
3. zhparser（中文分词，需要 SCWS 词典，最正统但部署最重）

**修正**：P2 关键词通道改为 **pg_trgm 相似度 + ILIKE 加速**，GIN 三元组索引；
文档明确区分"英文 FTS（tsvector）与中文（pg_trgm）"两套机制。

**面试价值**："中文检索不能用 Postgres 默认分词"——这是真踩过坑的人才说得出的细节。

### 评审结论
三个缺陷都在 P0/P1/P2 内，修正后设计自洽。这也示范了评审的**提问模板**：
数据从哪来（元数据来源）、数据怎么用（下游消费方）、坏了怎么发现（测试与可观测性）。

