package com.springagent.diagnosis.domain.dto.stream;

import java.util.UUID;

public record StreamMetadata(String protocolVersion, UUID conversationId) {
}
