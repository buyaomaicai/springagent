package com.springagent.knowledge.source;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * 从 sources.yml 加载知识来源注册表。
 *
 * <p>git 来源按 yml 中的文档清单解析（本地文件为 raw/&lt;id&gt;/&lt;文件名&gt;）；
 * web 来源按目录发现（raw/&lt;id&gt;/ 下的 .html 快照），sourceUrl 由 root_url 拼接。
 * 元数据全部取自已声明的 yml 字段，不靠文件名推断。</p>
 */
@Component
public class YamlSourceRegistry implements SourceRegistry {

    private final Path sourcesFile;
    private final Path rawRoot;

    public YamlSourceRegistry(
            @Value("${knowledge.source-registry.path:knowledge-base/sources.yml}")
            String sourcesPath,
            @Value("${knowledge.raw-root:knowledge-base/raw}")
            String rawRootPath
    ) {
        this.sourcesFile = Path.of(sourcesPath);
        this.rawRoot = Path.of(rawRootPath);
    }

    @Override
    public List<SourceDefinition> load() {
        Map<String, Object> root = readYaml();
        List<SourceDefinition> definitions = new ArrayList<>();
        definitions.addAll(loadGitSources(root));
        definitions.addAll(loadWebSources(root));
        return List.copyOf(definitions);
    }

    private List<SourceDefinition> loadGitSources(Map<String, Object> root) {
        List<SourceDefinition> result = new ArrayList<>();
        for (Object item : asList(root.get("git_sources"))) {
            Map<String, Object> source = asMap(item);
            String id = (String) source.get("id");
            String component = (String) source.get("component");
            String language = (String) source.get("language");
            String defaultSourceType = (String) source.getOrDefault(
                    "default_source_type",
                    "OTHER"
            );
            String urlPrefix = (String) source.get("source_url_prefix");

            List<SourceDocument> documents = new ArrayList<>();
            for (Object docItem : asList(source.get("documents"))) {
                Map<String, Object> doc = asMap(docItem);
                String fileName = Path.of((String) doc.get("path"))
                        .getFileName()
                        .toString();
                String sourceType = (String) doc.getOrDefault(
                        "source_type",
                        defaultSourceType
                );
                String targetVersion = (String) doc.get("target_version");
                String sourceUrl = urlPrefix == null
                        ? ""
                        : urlPrefix + stripExtension(fileName);
                documents.add(new SourceDocument(
                        fileName,
                        sourceType,
                        targetVersion,
                        sourceUrl
                ));
            }
            result.add(new SourceDefinition(
                    id,
                    component,
                    language,
                    rawRoot.resolve(id),
                    List.copyOf(documents)
            ));
        }
        return result;
    }

    private List<SourceDefinition> loadWebSources(Map<String, Object> root) {
        List<SourceDefinition> result = new ArrayList<>();
        for (Object item : asList(root.get("web_sources"))) {
            Map<String, Object> source = asMap(item);
            String id = (String) source.get("id");
            String rootUrl = (String) source.get("root_url");
            String component = (String) source.get("component");
            String language = (String) source.get("language");
            String sourceType = (String) source.getOrDefault(
                    "source_type",
                    "OTHER"
            );
            String targetVersion = (String) source.get("target_version");
            Path rootPath = rawRoot.resolve(id);

            List<SourceDocument> documents = new ArrayList<>();
            if (Files.isDirectory(rootPath)) {
                try (Stream<Path> files = Files.list(rootPath)) {
                    files.filter(file -> file.getFileName()
                                    .toString()
                                    .endsWith(".html"))
                            .sorted()
                            .forEach(file -> {
                                String fileName = file.getFileName().toString();
                                String url = rootUrl
                                        + (rootUrl.endsWith("/") ? "" : "/")
                                        + fileName;
                                documents.add(new SourceDocument(
                                        fileName,
                                        sourceType,
                                        targetVersion,
                                        url
                                ));
                            });
                } catch (IOException exception) {
                    throw new IllegalStateException(
                            "读取知识来源目录失败: " + rootPath,
                            exception
                    );
                }
            }
            result.add(new SourceDefinition(
                    id,
                    component,
                    language,
                    rootPath,
                    List.copyOf(documents)
            ));
        }
        return result;
    }

    private Map<String, Object> readYaml() {
        if (!Files.isRegularFile(sourcesFile)) {
            throw new IllegalStateException(
                    "知识来源注册表不存在: " + sourcesFile
            );
        }
        try (InputStream in = Files.newInputStream(sourcesFile)) {
            Object loaded = new Yaml().load(in);
            if (!(loaded instanceof Map<?, ?> map)) {
                throw new IllegalStateException(
                        "sources.yml 根节点必须是映射"
                );
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return typed;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "读取知识来源注册表失败: " + sourcesFile,
                    exception
            );
        }
    }

    private List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private Map<String, Object> asMap(Object value) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = value instanceof Map<?, ?>
                ? (Map<String, Object>) value
                : Map.of();
        return map;
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
