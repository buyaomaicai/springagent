package com.springagent.knowledge.chunking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 固定大小滑动窗口切分工具（语言无关的简化实现：字符窗口）。
 *
 * <p>生产环境可替换为 tokenizer 版本；overlap 使相邻块共享重叠文本，
 * 避免关键实体恰好落在切分边界被切断。</p>
 */
final class FixedWindow {

    private FixedWindow() {
    }

    /**
     * 按固定窗口切分文本，相邻窗口共享 overlap 个字符的重叠。
     *
     * @param overlap 必须小于 chunkSize，否则窗口会倒退
     */
    static List<Chunk> split(String text, int chunkSize, int overlap) {
        int safeOverlap = Math.max(0, Math.min(overlap, chunkSize - 1));
        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String piece = text.substring(start, end).strip();
            if (!piece.isEmpty()) {
                chunks.add(new Chunk(piece, Map.of()));
            }
            if (end >= text.length()) {
                break;
            }
            start = end - safeOverlap;
        }
        return chunks;
    }
}
