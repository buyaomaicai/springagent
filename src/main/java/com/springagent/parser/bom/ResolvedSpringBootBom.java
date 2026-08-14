package com.springagent.parser.bom;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 一次 Spring Boot BOM 解析的不可变结果。
 */
public record ResolvedSpringBootBom(
        String springBootVersion,
        boolean available,
        Map<MavenDependencyKey, String> managedVersions,
        List<String> warnings
) {

    public ResolvedSpringBootBom {
        managedVersions = managedVersions == null
                ? Map.of()
                : Map.copyOf(managedVersions);
        warnings = warnings == null
                ? List.of()
                : List.copyOf(warnings);
    }

    public static ResolvedSpringBootBom unavailable(
            String springBootVersion,
            String warning
    ) {
        return new ResolvedSpringBootBom(
                springBootVersion,
                false,
                Map.of(),
                List.of(warning)
        );
    }

    /**
     * 当前项目没有使用 Spring Boot 时的空结果。
     */
    public static ResolvedSpringBootBom notApplicable() {
        return new ResolvedSpringBootBom(
                null,
                false,
                Map.of(),
                List.of()
        );
    }

    public Optional<String> findVersion(MavenDependencyKey key) {
        return Optional.ofNullable(managedVersions.get(key));
    }
}
