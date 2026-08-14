package com.springagent.parser.impl;

import com.springagent.diagnosis.model.ProjectDependency;
import com.springagent.diagnosis.model.ProjectInput;
import com.springagent.diagnosis.model.VersionSource;
import com.springagent.parser.ArtifactType;
import com.springagent.parser.ProjectArtifactParser;
import com.springagent.parser.bom.MavenDependencyKey;
import com.springagent.parser.bom.ResolvedSpringBootBom;
import com.springagent.parser.bom.SpringBootBomResolver;
import com.springagent.parser.exception.ProjectArtifactParseException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.springframework.stereotype.Component;

/**
 * 将一个原始 Maven {@code pom.xml} 解析为诊断模块使用的 {@link ProjectInput}。
 *
 * <p>这个解析器只分析当前上传的 POM，不构建 Maven effective model。也就是说，它不会下载
 * Parent，不会激活 Profile，也不会计算传递依赖。识别出 Spring Boot 版本后，它会通过
 * {@link SpringBootBomResolver} 尝试读取本机 Maven 仓库中已有的 Spring Boot BOM，但不会联网下载
 * 或执行 Maven。这样可以在保持过程可控的同时，补全一部分由 Boot 管理的直接依赖版本。</p>
 *
 * <p>主要处理流程如下：</p>
 * <ol>
 *     <li>限制输入大小，并拒绝空内容。</li>
 *     <li>拒绝带有 DOCTYPE 或实体声明的 XML。</li>
 *     <li>使用 MavenXpp3Reader 将 XML 读取为原始 Maven Model。</li>
 *     <li>提取项目坐标、Java 版本、Spring Boot 版本、模块和直接依赖。</li>
 *     <li>将不能可靠解析的信息标记为 warning 或 UNRESOLVED。</li>
 * </ol>
 */
@Component
public class PomXmlParser implements ProjectArtifactParser<ProjectInput> {

    /**
     * Spring Boot BOM 的解析策略由外部注入，Parser 本身不关心 BOM 来自本地文件还是其他实现。
     */
    private final SpringBootBomResolver springBootBomResolver;

    /**
     * 单个 POM 最大允许 1 MiB，避免超大输入长期占用内存和解析线程。
     */
    private static final int MAX_CONTENT_SIZE = 1024 * 1024;

    /**
     * 当前解析器仅支持 Maven POM 4.0.0 模型。
     */
    private static final String SUPPORTED_MODEL_VERSION = "4.0.0";

    /**
     * 只匹配整个值都是属性引用的形式，例如 ${spring-boot.version}。
     * 当前版本不会处理 1.${minor}.0 这种字符串内部插值。
     */
    private static final Pattern EXACT_PROPERTY_REFERENCE =
            Pattern.compile("^\\$\\{([^}]+)}$");

    public PomXmlParser(SpringBootBomResolver springBootBomResolver) {
        this.springBootBomResolver = springBootBomResolver;
    }

    /**
     * 声明当前实现负责处理 POM_XML 类型材料。
     *
     * @return 固定返回 POM_XML
     */
    @Override
    public ArtifactType supportedType() {
        return ArtifactType.POM_XML;
    }

    /**
     * 解析一个 POM 输入流。
     *
     * <p>调用者仍然拥有传入的 InputStream，本方法不会主动关闭它。输入会先被复制到一个大小受限的
     * byte 数组，后续安全检查和 Maven 解析都基于该数组进行，避免重复消费原始流。</p>
     *
     * @param input POM 文件输入流
     * @return 不可依赖 Maven Model 可变状态的项目快照
     * @throws ProjectArtifactParseException 输入为空、过大、不安全、格式错误或读取失败时抛出
     */
    @Override
    public ProjectInput parse(InputStream input) {
        // 第一步先完整读取受限大小的内容，后续逻辑不再直接操作调用方的流。
        byte[] content = readContent(input);

        // 在交给 XML Reader 之前拒绝危险声明，避免外部实体读取本机文件或访问网络。
        rejectUnsafeXml(content);

        try {
            // Reader 不需要注入 Spring Bean；每次解析创建实例可以避免共享解析器内部状态。
            MavenXpp3Reader reader = new MavenXpp3Reader();

            // strict=true 要求输入符合 Maven POM 模型，避免宽松接受未知或错误结构。
            Model model = reader.read(
                    new ByteArrayInputStream(content),
                    true
            );

            // XML 能被读取不代表它一定是本系统支持的有效 POM，还需要做业务层校验。
            validateModel(model);

            // 将 Maven 原始模型显式映射为系统自己的不可变诊断输入。
            return toProjectInput(model);
        } catch (XmlPullParserException exception) {
            // XML 语法或 Maven 模型结构错误统一归类为 MALFORMED_CONTENT。
            throw new ProjectArtifactParseException(
                    ArtifactType.POM_XML,
                    ProjectArtifactParseException.Reason.MALFORMED_CONTENT,
                    "POM XML 格式错误",
                    exception
            );
        } catch (IOException exception) {
            // Maven Reader 仍声明可能发生 I/O 异常，统一转换为解析领域异常。
            throw new ProjectArtifactParseException(
                    ArtifactType.POM_XML,
                    ProjectArtifactParseException.Reason.IO_ERROR,
                    "读取 POM XML 失败",
                    exception
            );
        }
    }

    /**
     * 将输入流读取成内存中的字节数组，并执行空内容和大小限制检查。
     */
    private byte[] readContent(InputStream input) {
        // null 表示调用者没有提供任何输入，而不是一个格式错误的 XML。
        if (input == null) {
            throw parseException(
                    ProjectArtifactParseException.Reason.EMPTY_CONTENT,
                    "POM XML 不能为空"
            );
        }

        try {
            // 多读 1 个字节用于判断是否超过限制，避免先读取无限内容再检查大小。
            byte[] content = input.readNBytes(MAX_CONTENT_SIZE + 1);

            // 非 null 的 InputStream 也可能立即到达 EOF，所以仍需检查实际字节数。
            if (content.length == 0) {
                throw parseException(
                        ProjectArtifactParseException.Reason.EMPTY_CONTENT,
                        "POM XML 不能为空"
                );
            }

            // 读到了第 MAX+1 个字节就说明文件已超限，不需要继续读取剩余内容。
            if (content.length > MAX_CONTENT_SIZE) {
                throw parseException(
                        ProjectArtifactParseException.Reason.CONTENT_TOO_LARGE,
                        "POM XML 不能超过 1 MB"
                );
            }

            // 返回独立字节数组，安全扫描和模型解析都可以从头读取它。
            return content;
        } catch (IOException exception) {
            // 保留原始 cause 供服务端日志定位，同时向上层提供稳定的失败分类。
            throw new ProjectArtifactParseException(
                    ArtifactType.POM_XML,
                    ProjectArtifactParseException.Reason.IO_ERROR,
                    "读取 POM XML 失败",
                    exception
            );
        }
    }

    /**
     * 拒绝当前安全边界不允许的 XML 声明。
     *
     * <p>POM 按项目约定使用 UTF-8。转换为大写时使用 Locale.ROOT，避免服务器地区设置影响安全关键字
     * 判断。DOCTYPE 和 ENTITY 对普通 Maven POM 没有必要，却可能被用于 XXE 攻击。</p>
     */
    private void rejectUnsafeXml(byte[] content) {
        String xml = new String(content, StandardCharsets.UTF_8)
                .toUpperCase(Locale.ROOT);

        // 任意一种声明出现都立即拒绝，不让 Maven Reader 尝试处理实体。
        if (xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY")) {
            throw parseException(
                    ProjectArtifactParseException.Reason.UNSAFE_CONTENT,
                    "POM XML 不允许包含 DOCTYPE 或外部实体"
            );
        }
    }

    /**
     * 校验 Maven Reader 产出的原始模型是否满足本系统的最低要求。
     */
    private void validateModel(Model model) {
        // 正常 Reader 不应返回 null，但保留防御性检查，避免后续出现难定位的空指针。
        if (model == null) {
            throw parseException(
                    ProjectArtifactParseException.Reason.MALFORMED_CONTENT,
                    "未读取到 Maven 项目模型"
            );
        }

        // 不接受未知模型版本，因为字段语义和默认规则可能已经变化。
        if (!SUPPORTED_MODEL_VERSION.equals(model.getModelVersion())) {
            throw parseException(
                    ProjectArtifactParseException.Reason.UNSUPPORTED_FORMAT,
                    "只支持 Maven POM 4.0.0"
            );
        }

        // artifactId 是项目自身身份，不能像 groupId 和 version 那样从 Parent 继承。
        if (!hasText(model.getArtifactId())) {
            throw parseException(
                    ProjectArtifactParseException.Reason.MISSING_REQUIRED_FIELD,
                    "POM XML 缺少 artifactId"
            );
        }
    }

    /**
     * 将 Maven 的可变 Model 显式转换为系统使用的 ProjectInput 快照。
     *
     * <p>这里不使用 Bean 属性复制，因为 Model 与 ProjectInput 的字段结构和语义不同。例如 Java 版本
     * 需要从 properties 推断，依赖需要转换类型，Spring Boot 版本还需要综合 Parent 与 BOM。</p>
     */
    private ProjectInput toProjectInput(Model model) {
        // warnings 保存“能够继续解析，但结果不完整或存在冲突”的非致命问题。
        List<String> warnings = new ArrayList<>();

        // Parent 可能为空，因此后续读取 Parent 字段都需要空值保护。
        Parent parent = model.getParent();

        // Maven 子模块可以省略 groupId，此时继承 Parent 的 groupId。
        String groupId = firstNonBlank(
                model.getGroupId(),
                parent == null ? null : parent.getGroupId()
        );

        // Maven 子模块也可以省略 version，此时继承 Parent 的 version。
        String version = firstNonBlank(
                model.getVersion(),
                parent == null ? null : parent.getVersion()
        );

        // Maven 约定未声明 packaging 时默认为 jar。
        String packaging = firstNonBlank(
                model.getPackaging(),
                "jar"
        );

        // Java 版本来自当前 POM 的编译相关 properties。
        String javaVersion = detectJavaVersion(model);

        // Spring Boot 版本可能来自 Boot Parent，也可能来自显式导入的 Boot BOM。
        String springBootVersion = detectSpringBootVersion(
                model,
                warnings
        );

        // 只在已经确定 Spring Boot 版本后解析 BOM；没有使用 Boot 的项目直接得到空结果。
        ResolvedSpringBootBom springBootBom = resolveSpringBootBom(
                springBootVersion,
                warnings
        );

        // 当前解析器不计算 Profile 激活条件，因此明确告知下游快照可能不包含 Profile 内容。
        if (!model.getProfiles().isEmpty()) {
            warnings.add(
                    "检测到 Maven Profile，当前解析结果未应用 Profile 配置"
            );
        }

        // 显式构造 ProjectInput，确保每一个目标字段的来源和默认规则都清晰可见。
        return new ProjectInput(
                groupId,
                model.getArtifactId(),
                version,
                packaging,
                javaVersion,
                springBootVersion,
                mapDependencies(model, springBootBom),
                // 插件解析尚未实现，因此当前返回空列表，而不是 null。
                List.of(),
                // Maven Model 中的集合是可变的，复制后避免外部修改解析结果。
                List.copyOf(model.getModules()),
                List.copyOf(warnings)
        );
    }

    /**
     * 从当前 POM 的 properties 推断 Java 编译版本。
     *
     * <p>优先使用 maven.compiler.release，因为它同时约束语言级别和目标 Java API；其次兼容
     * Spring Boot 常用的 java.version；最后回退到旧式 maven.compiler.target。</p>
     */
    private String detectJavaVersion(Model model) {
        Properties properties = model.getProperties();

        // firstNonBlank 按参数顺序返回第一个有文本的配置值。
        return firstNonBlank(
                properties.getProperty("maven.compiler.release"),
                properties.getProperty("java.version"),
                properties.getProperty("maven.compiler.target")
        );
    }

    private String detectSpringBootVersion(
            Model model,
            List<String> warnings
    ) {
        // MavenXpp3Reader 只提供当前 POM 自己声明的 properties，不包含远程 Parent 的属性。
        Properties properties = model.getProperties();

        // 先检查项目是否使用 Spring Boot Parent，并尝试解析 Parent 版本。
        String parentVersion = detectSpringBootParentVersion(
                model.getParent(),
                properties,
                warnings
        );

        // 再检查当前 POM 是否显式 import 了 spring-boot-dependencies BOM。
        Dependency springBootBom = findSpringBootBom(model);
        if (springBootBom == null) {
            // 没有显式 BOM 时，Boot Parent 版本就是当前能够识别的最佳结果。
            return parentVersion;
        }

        // BOM 版本既可能是 3.2.5，也可能是 ${spring-boot.version}。
        String bomVersion = resolveVersionValue(
                springBootBom.getVersion(),
                properties
        );

        // 属性缺失或循环引用时无法得到可靠版本，保留 Parent 版本作为回退值。
        if (!hasText(bomVersion)) {
            warnings.add(
                    "无法解析 Spring Boot BOM 版本: "
                            + springBootBom.getVersion()
            );
            return parentVersion;
        }

        // 同时存在 Parent 和显式 BOM 且版本不一致，说明项目的 Boot 依赖管理存在混用风险。
        if (hasText(parentVersion)
                && !parentVersion.equals(bomVersion)) {
            warnings.add(
                    "Spring Boot Parent 版本 "
                            + parentVersion
                            + " 与导入 BOM 版本 "
                            + bomVersion
                            + " 不一致，优先使用 BOM 版本"
            );
        }

        // 当前 POM 的显式 dependencyManagement 比继承配置更接近实际依赖管理意图，因此优先 BOM。
        return bomVersion;
    }

    /**
     * 根据已经识别出的 Spring Boot 版本加载其依赖管理快照。
     *
     * <p>解析失败不是 POM 语法错误，因此不会中断整个项目解析；Resolver 返回的说明会追加到
     * warnings，让后续诊断能够区分“项目没有版本”和“本地 BOM 不可用”。</p>
     */
    private ResolvedSpringBootBom resolveSpringBootBom(
            String springBootVersion,
            List<String> warnings
    ) {
        if (!hasText(springBootVersion)) {
            return ResolvedSpringBootBom.notApplicable();
        }

        ResolvedSpringBootBom resolved =
                springBootBomResolver.resolve(springBootVersion);
        warnings.addAll(resolved.warnings());
        return resolved;
    }

    /**
     * 当 Parent 坐标是 spring-boot-starter-parent 时解析其版本。
     */
    private String detectSpringBootParentVersion(
            Parent parent,
            Properties properties,
            List<String> warnings
    ) {
        // 只有 groupId 和 artifactId 都匹配时才认为这是 Spring Boot Parent，避免名称相似造成误判。
        if (parent == null
                || !"org.springframework.boot".equals(parent.getGroupId())
                || !"spring-boot-starter-parent"
                .equals(parent.getArtifactId())) {
            return null;
        }

        // 支持字面量版本，也支持当前 POM properties 中定义的精确属性引用。
        String version = resolveVersionValue(
                parent.getVersion(),
                properties
        );

        // Parent 已确认是 Spring Boot，但版本无法解析时不终止整个 POM，只记录结果不完整。
        if (!hasText(version)) {
            warnings.add(
                    "无法解析 Spring Boot Parent 版本: "
                            + parent.getVersion()
            );
        }
        return version;
    }

    /**
     * 在当前 POM 的 dependencyManagement 中寻找显式导入的 Spring Boot BOM。
     *
     * <p>只匹配完整 Maven BOM 语义：固定坐标、type=pom、scope=import。仅仅出现
     * spring-boot-dependencies 这个 artifactId 并不足以说明它是一个生效的 BOM。</p>
     */
    private Dependency findSpringBootBom(Model model) {
        DependencyManagement dependencyManagement =
                model.getDependencyManagement();

        // 没有 dependencyManagement 就不可能存在当前 POM 显式导入的 BOM。
        if (dependencyManagement == null) {
            return null;
        }

        // 保留 POM 声明顺序遍历，找到第一个合法的 Spring Boot BOM 导入。
        for (Dependency dependency
                : dependencyManagement.getDependencies()) {
            boolean springBootBom =
                    "org.springframework.boot"
                            .equals(dependency.getGroupId())
                            && "spring-boot-dependencies"
                            .equals(dependency.getArtifactId())
                            && "pom".equals(dependency.getType())
                            && "import".equals(dependency.getScope());

            if (springBootBom) {
                // 返回原始 Dependency，后续还需要从中读取和解析 version。
                return dependency;
            }
        }

        // dependencyManagement 存在，但没有符合完整 BOM 语义的 Spring Boot 条目。
        return null;
    }

    /**
     * 将一个版本表达式解析成最终字符串。
     *
     * <p>当前只使用本 POM 的 properties；解析不到时返回 null，调用方决定回退或记录 warning。</p>
     */
    private String resolveVersionValue(
            String version,
            Properties properties
    ) {
        // null、空串和纯空白都没有可解析的版本信息。
        if (!hasText(version)) {
            return null;
        }

        // 每次顶层解析使用新的集合，以检测本次属性链中的循环引用。
        return resolveProperty(
                version,
                properties,
                new HashSet<>()
        );
    }

    /**
     * 按顺序返回第一个非 null、非空、非纯空白字符串。
     */
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }

        return null;
    }

    /**
     * 判断字符串是否包含实际文本。
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 创建带有统一材料类型的解析异常，减少重复构造参数。
     */
    private ProjectArtifactParseException parseException(
            ProjectArtifactParseException.Reason reason,
            String message
    ) {
        return new ProjectArtifactParseException(
                ArtifactType.POM_XML,
                reason,
                message
        );
    }

    /**
     * 将当前 POM 的直接依赖转换为系统自己的 ProjectDependency。
     *
     * <p>这里只处理 model.getDependencies()，不计算传递依赖。版本优先使用依赖自身声明，其次尝试
     * 当前 POM 的 dependencyManagement，再尝试 Spring Boot BOM，仍无法确定时标记为
     * UNRESOLVED。</p>
     */
    private List<ProjectDependency> mapDependencies(
            Model model,
            ResolvedSpringBootBom springBootBom
    ) {
        // 当前 POM properties 用于解析 ${xxx.version} 形式的依赖版本。
        Properties properties = model.getProperties();

        // 先把 dependencyManagement 建成索引，避免每个直接依赖都重复遍历管理列表。
        Map<String, Dependency> managedDependencies =
                indexManagedDependencies(model);

        // 使用可变列表逐项构造，方法返回前再复制为不可修改列表。
        List<ProjectDependency> result = new ArrayList<>();

        for (Dependency dependency : model.getDependencies()) {
            // 用 Maven 依赖身份 key 查找当前 POM 中对应的版本管理条目。
            Dependency managedDependency = managedDependencies.get(
                    dependencyKey(dependency)
            );

            // BOM 使用结构化 key，避免通过字符串拼接时遗漏 Maven 的 type/classifier 语义。
            String springBootManagedVersion = springBootBom
                    .findVersion(mavenDependencyKey(dependency))
                    .orElse(null);

            // 同时得到最终版本字符串和版本来源，供诊断阶段判断可信程度。
            ResolvedVersion resolvedVersion = resolveDependencyVersion(
                    dependency,
                    managedDependency,
                    properties,
                    springBootManagedVersion
            );

            // Maven Model 类型不能直接暴露给诊断层，因此显式转换为内部 record。
            ProjectDependency projectDependency =
                    new ProjectDependency(
                            dependency.getGroupId(),
                            dependency.getArtifactId(),
                            resolvedVersion.value(),
                            // Maven 未声明 scope 时，直接依赖默认使用 compile。
                            firstNonBlank(
                                    dependency.getScope(),
                                    "compile"
                            ),
                            // Maven 未声明 type 时，普通依赖默认使用 jar。
                            firstNonBlank(
                                    dependency.getType(),
                                    "jar"
                            ),
                            dependency.isOptional(),
                            resolvedVersion.source(),
                            mapExclusions(dependency)
                    );

            // 保留 POM 中直接依赖的声明顺序，便于展示和测试。
            result.add(projectDependency);
        }

        // 防止调用方修改 Parser 已经生成的快照内容。
        return List.copyOf(result);
    }

    /**
     * 将 Maven Exclusion 转换为简洁的 groupId:artifactId 坐标列表。
     */
    private List<String> mapExclusions(
            Dependency dependency
    ) {
        List<String> result = new ArrayList<>();

        for (Exclusion exclusion : dependency.getExclusions()) {
            // Exclusion 的有效身份由 groupId 和 artifactId 组成，不包含版本。
            String coordinate =
                    normalizeKeyPart(exclusion.getGroupId())
                            + ":"
                            + normalizeKeyPart(
                            exclusion.getArtifactId()
                    );

            result.add(coordinate);
        }

        // 返回只读快照，避免外部改变某个依赖的排除项。
        return List.copyOf(result);
    }

    /**
     * 为当前 POM 自己声明的 dependencyManagement 建立依赖身份索引。
     *
     * <p>导入 BOM 的条目也会出现在这个 Map 中，但当前 POM 的索引本身不展开导入内容。
     * Spring Boot BOM 的管理版本由独立的 SpringBootBomResolver 提供。</p>
     */
    private Map<String, Dependency> indexManagedDependencies(
            Model model
    ) {
        DependencyManagement dependencyManagement =
                model.getDependencyManagement();

        // 没有 dependencyManagement 时返回共享空 Map，调用方无需进行 null 判断。
        if (dependencyManagement == null) {
            return Map.of();
        }

        Map<String, Dependency> result = new HashMap<>();

        for (Dependency dependency
                : dependencyManagement.getDependencies()) {
            // 相同 key 后出现的条目覆盖前面的条目；重复管理本身属于 POM 质量问题。
            result.put(
                    dependencyKey(dependency),
                    dependency
            );
        }

        return result;
    }

    /**
     * 构造 Maven 依赖管理匹配使用的身份 key。
     *
     * <p>不能只使用 groupId:artifactId，因为同一 artifact 还可能通过不同 type 或 classifier 出现。</p>
     */
    private String dependencyKey(Dependency dependency) {
        return String.join(
                ":",
                normalizeKeyPart(dependency.getGroupId()),
                normalizeKeyPart(dependency.getArtifactId()),
                // Maven 依赖 type 的默认值是 jar，索引和查询必须使用相同默认规则。
                firstNonBlank(dependency.getType(), "jar"),
                normalizeKeyPart(dependency.getClassifier())
        );
    }

    /**
     * 构造查询 Spring Boot BOM 时使用的结构化 Maven 依赖身份。
     */
    private MavenDependencyKey mavenDependencyKey(
            Dependency dependency
    ) {
        return new MavenDependencyKey(
                dependency.getGroupId(),
                dependency.getArtifactId(),
                dependency.getType(),
                dependency.getClassifier()
        );
    }

    /**
     * 将依赖 key 的可空部分规范化为稳定字符串，防止 String.join 遇到 null。
     */
    private String normalizeKeyPart(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 按 Maven 声明优先级确定某个直接依赖的版本和版本来源。
     */
    private ResolvedVersion resolveDependencyVersion(
            Dependency dependency,
            Dependency managedDependency,
            Properties properties,
            String springBootManagedVersion
    ) {
        // 先读取直接依赖自己声明的 version。
        String declaredVersion = dependency.getVersion();

        // 直接声明始终优先于 dependencyManagement。
        if (hasText(declaredVersion)) {
            return resolveDeclaredVersion(
                    declaredVersion,
                    properties
            );
        }

        // 直接依赖未写版本时，尝试当前 POM 中匹配的 dependencyManagement 条目。
        if (managedDependency != null
                && hasText(managedDependency.getVersion())) {
            ResolvedVersion managedVersion =
                    resolveDeclaredVersion(
                            managedDependency.getVersion(),
                            properties
                    );

            // 管理条目的属性仍无法解析时，不能错误地标记为已由 dependencyManagement 成功解析。
            if (managedVersion.source()
                    == VersionSource.UNRESOLVED) {
                return managedVersion;
            }

            // 无论管理条目内部是字面量还是 property，直接依赖的来源都记为 DEPENDENCY_MANAGEMENT。
            return new ResolvedVersion(
                    managedVersion.value(),
                    VersionSource.DEPENDENCY_MANAGEMENT
            );
        }

        // 项目自身没有指定版本时，才允许 Spring Boot BOM 提供默认管理版本。
        if (hasText(springBootManagedVersion)) {
            return new ResolvedVersion(
                    springBootManagedVersion,
                    VersionSource.SPRING_BOOT_BOM
            );
        }

        // 当前已知的声明、管理条目和 Boot BOM 都没有可用版本。
        return new ResolvedVersion(
                null,
                VersionSource.UNRESOLVED
        );
    }

    /**
     * 解析依赖或管理条目直接写在 version 字段中的值。
     */
    private ResolvedVersion resolveDeclaredVersion(
            String declaredVersion,
            Properties properties
    ) {
        // 判断整个版本值是否恰好是 ${property.name}。
        Matcher matcher = EXACT_PROPERTY_REFERENCE.matcher(
                declaredVersion
        );

        // 不是属性表达式时，原样保留并标记为直接声明。
        if (!matcher.matches()) {
            return new ResolvedVersion(
                    declaredVersion,
                    VersionSource.DECLARED
            );
        }

        // 属性可能继续引用另一个属性，所以交给递归解析器处理。
        String resolvedValue = resolveProperty(
                declaredVersion,
                properties,
                new HashSet<>()
        );

        // 属性不存在、为空或形成循环时保留原表达式，并明确标记无法解析。
        if (!hasText(resolvedValue)) {
            return new ResolvedVersion(
                    declaredVersion,
                    VersionSource.UNRESOLVED
            );
        }

        // 成功得到最终字符串时，记录该版本来自当前 POM 的 property。
        return new ResolvedVersion(
                resolvedValue,
                VersionSource.PROPERTY
            );
    }

    /**
     * 递归解析整个字符串形式的 Maven property 引用。
     *
     * <p>例如 ${library.version} -> ${base.version} -> 1.2.3。resolvingKeys 记录当前递归链
     * 已经访问过的属性名，用于检测 a -> b -> a 这样的循环。</p>
     *
     * @param value 当前待解析的字面量或精确属性引用
     * @param properties 当前 POM 直接声明的 properties
     * @param resolvingKeys 当前递归链中正在解析的属性名
     * @return 最终字面量；属性缺失或循环引用时返回 null
     */
    private String resolveProperty(
            String value,
            Properties properties,
            Set<String> resolvingKeys
    ) {
        // 只有整个值匹配 ${name} 才进行替换，普通版本字符串直接作为递归终点返回。
        Matcher matcher = EXACT_PROPERTY_REFERENCE.matcher(value);

        if (!matcher.matches()) {
            return value;
        }

        // 正则捕获组中是不含 ${} 的真实属性名。
        String propertyName = matcher.group(1);

        // Set.add 返回 false 表示当前递归链已出现同名属性，即形成循环引用。
        if (!resolvingKeys.add(propertyName)) {
            return null;
        }

        try {
            // MavenXpp3Reader 的 Model.properties 只包含当前原始 POM 的属性。
            String propertyValue = properties.getProperty(propertyName);

            // 未声明或空属性不能生成可靠版本。
            if (!hasText(propertyValue)) {
                return null;
            }

            // 属性值仍可能是另一个精确属性引用，因此继续递归。
            return resolveProperty(
                    propertyValue,
                    properties,
                    resolvingKeys
            );
        } finally {
            // 无论成功、失败还是抛异常，都移除当前属性，保证兄弟解析分支互不干扰。
            resolvingKeys.remove(propertyName);
        }
    }

    /**
     * Parser 内部使用的组合返回值，同时携带解析后的版本和来源。
     *
     * @param value 最终版本；完全无法确定时可以为 null
     * @param source 版本来自直接声明、property、dependencyManagement、Spring Boot BOM，或仍未解析
     */
    private record ResolvedVersion(
            String value,
            VersionSource source
    ) {
    }
}
