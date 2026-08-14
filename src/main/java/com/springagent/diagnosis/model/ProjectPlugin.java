package com.springagent.diagnosis.model;

import java.util.List;

public record ProjectPlugin(
        String groupId,
        String artifactId,
        String version,
        List<String> goals
) {
}