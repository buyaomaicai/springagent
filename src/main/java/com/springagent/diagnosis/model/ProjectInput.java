package com.springagent.diagnosis.model;

import java.util.List;

public record ProjectInput(
        String groupId,
        String artifactId,
        String version,
        String packaging,
        String javaVersion,
        String springBootVersion,
        List<ProjectDependency> dependencies,
        List<ProjectPlugin> plugins,
        List<String> modules,
        List<String> warnings
) {
}