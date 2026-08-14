package com.springagent.parser.bom;

/**
 * Maven dependencyManagement 中用于匹配依赖的稳定身份。
 */
public record MavenDependencyKey(
        String groupId,
        String artifactId,
        String type,
        String classifier
) {

    public MavenDependencyKey {
        groupId = normalize(groupId);
        artifactId = normalize(artifactId);
        type = hasText(type) ? type.trim() : "jar";
        classifier = normalize(classifier);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
