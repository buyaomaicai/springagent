package com.springagent.diagnosis.domain.dto.stream;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record StreamEvent<T>(
        String requestId,
        long sequence,
        T data,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp
) {

    public static <T> StreamEvent<T> of(
            String requestId,
            long sequence,
            T data
    ) {
        return new StreamEvent<>(requestId, sequence, data, Instant.now());
    }
}
