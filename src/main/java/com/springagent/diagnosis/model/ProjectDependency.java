package com.springagent.diagnosis.model;

import java.util.List;

public record ProjectDependency(
        String groupId,
        String artifactId,
        String version,
        String scope,
        String type,
        boolean optional,
        VersionSource versionSource,
        List<String> exclusions
) {
}