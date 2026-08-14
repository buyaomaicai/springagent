package com.springagent.parser.bom;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.springframework.stereotype.Component;

/**
 * 从本机 Maven 仓库读取 Spring Boot BOM，不执行下载或 Maven 构建。
 */
@Component
public class LocalMavenSpringBootBomResolver
        implements SpringBootBomResolver {

    private static final long MAX_BOM_SIZE = 2L * 1024 * 1024;
    private static final Pattern SAFE_VERSION =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*$");
    private static final Pattern EXACT_PROPERTY_REFERENCE =
            Pattern.compile("^\\$\\{([^}]+)}$");

    private final Path localRepository;
    private final ConcurrentMap<String, ResolvedSpringBootBom> cache =
            new ConcurrentHashMap<>();

    public LocalMavenSpringBootBomResolver() {
        this(defaultLocalRepository());
    }

    public LocalMavenSpringBootBomResolver(Path localRepository) {
        this.localRepository = localRepository
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public ResolvedSpringBootBom resolve(String springBootVersion) {
        if (!hasText(springBootVersion)
                || !SAFE_VERSION.matcher(springBootVersion).matches()) {
            return ResolvedSpringBootBom.unavailable(
                    springBootVersion,
                    "Spring Boot 版本格式不安全，无法定位本地 BOM: "
                            + springBootVersion
            );
        }

        ResolvedSpringBootBom cached = cache.get(springBootVersion);
        if (cached != null) {
            return cached;
        }

        ResolvedSpringBootBom resolved = loadBom(springBootVersion);
        if (resolved.available()) {
            cache.putIfAbsent(springBootVersion, resolved);
            return cache.get(springBootVersion);
        }
        return resolved;
    }

    private ResolvedSpringBootBom loadBom(String springBootVersion) {
        Path bomPath = resolveBomPath(springBootVersion);
        if (!Files.isRegularFile(bomPath)) {
            return ResolvedSpringBootBom.unavailable(
                    springBootVersion,
                    "本地 Maven 仓库不存在 Spring Boot BOM: " + bomPath
            );
        }

        try {
            if (Files.size(bomPath) > MAX_BOM_SIZE) {
                return ResolvedSpringBootBom.unavailable(
                        springBootVersion,
                        "Spring Boot BOM 超过 2 MiB 限制: " + bomPath
                );
            }

            try (InputStream input = Files.newInputStream(bomPath)) {
                Model model = new MavenXpp3Reader().read(input, true);
                return mapBom(springBootVersion, model);
            }
        } catch (IOException | XmlPullParserException exception) {
            return ResolvedSpringBootBom.unavailable(
                    springBootVersion,
                    "读取本地 Spring Boot BOM 失败: "
                            + exception.getMessage()
            );
        }
    }

    private ResolvedSpringBootBom mapBom(
            String expectedVersion,
            Model model
    ) {
        if (!"org.springframework.boot".equals(model.getGroupId())
                || !"spring-boot-dependencies"
                .equals(model.getArtifactId())
                || !expectedVersion.equals(model.getVersion())) {
            return ResolvedSpringBootBom.unavailable(
                    expectedVersion,
                    "本地文件不是预期的 Spring Boot BOM"
            );
        }

        DependencyManagement management = model.getDependencyManagement();
        if (management == null) {
            return ResolvedSpringBootBom.unavailable(
                    expectedVersion,
                    "Spring Boot BOM 缺少 dependencyManagement"
            );
        }

        Properties properties = new Properties();
        properties.putAll(model.getProperties());
        properties.setProperty("project.version", model.getVersion());
        properties.setProperty("pom.version", model.getVersion());

        Map<MavenDependencyKey, String> managedVersions =
                new HashMap<>();
        List<String> warnings = new ArrayList<>();
        int unresolvedCount = 0;
        int importedBomCount = 0;

        for (Dependency dependency : management.getDependencies()) {
            if (isImportedBom(dependency)) {
                importedBomCount++;
                continue;
            }

            String version = resolveProperty(
                    dependency.getVersion(),
                    properties,
                    new HashSet<>()
            );
            if (!hasText(version)) {
                unresolvedCount++;
                continue;
            }

            managedVersions.put(
                    dependencyKey(dependency),
                    version
            );
        }

        if (unresolvedCount > 0) {
            warnings.add(
                    "Spring Boot BOM 中有 "
                            + unresolvedCount
                            + " 个直接管理版本无法解析"
            );
        }
        if (importedBomCount > 0) {
            warnings.add(
                    "Spring Boot BOM 还导入了 "
                            + importedBomCount
                            + " 个嵌套 BOM，当前阶段尚未递归展开"
            );
        }

        return new ResolvedSpringBootBom(
                expectedVersion,
                true,
                managedVersions,
                warnings
        );
    }

    private Path resolveBomPath(String springBootVersion) {
        Path path = localRepository
                .resolve("org")
                .resolve("springframework")
                .resolve("boot")
                .resolve("spring-boot-dependencies")
                .resolve(springBootVersion)
                .resolve(
                        "spring-boot-dependencies-"
                                + springBootVersion
                                + ".pom"
                )
                .normalize();

        if (!path.startsWith(localRepository)) {
            throw new IllegalArgumentException(
                    "BOM path escapes local Maven repository"
            );
        }
        return path;
    }

    private MavenDependencyKey dependencyKey(Dependency dependency) {
        return new MavenDependencyKey(
                dependency.getGroupId(),
                dependency.getArtifactId(),
                dependency.getType(),
                dependency.getClassifier()
        );
    }

    private boolean isImportedBom(Dependency dependency) {
        return "pom".equals(dependency.getType())
                && "import".equals(dependency.getScope());
    }

    private String resolveProperty(
            String value,
            Properties properties,
            Set<String> resolvingKeys
    ) {
        if (!hasText(value)) {
            return null;
        }

        Matcher matcher = EXACT_PROPERTY_REFERENCE.matcher(value);
        if (!matcher.matches()) {
            return value;
        }

        String propertyName = matcher.group(1);
        if (!resolvingKeys.add(propertyName)) {
            return null;
        }

        try {
            return resolveProperty(
                    properties.getProperty(propertyName),
                    properties,
                    resolvingKeys
            );
        } finally {
            resolvingKeys.remove(propertyName);
        }
    }

    private static Path defaultLocalRepository() {
        String configuredRepository =
                System.getProperty("maven.repo.local");
        if (hasText(configuredRepository)) {
            return Path.of(configuredRepository);
        }
        return Path.of(
                System.getProperty("user.home"),
                ".m2",
                "repository"
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
