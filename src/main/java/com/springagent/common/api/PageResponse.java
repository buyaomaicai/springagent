package com.springagent.common.api;

import java.util.List;

/**
 * 统一分页响应，页码从 1 开始。
 */
public record PageResponse<T>(
        List<T> items,
        long total,
        int page,
        int size,
        long totalPages
) {

    public PageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
