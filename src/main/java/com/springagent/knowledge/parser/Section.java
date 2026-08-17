package com.springagent.knowledge.parser;

/**
 * 文档中的一个章节：标题 + 层级 + 正文。
 *
 * <p>解析器负责"识别结构"（把文档切成带层级的章节），
 * 分块器（P1）负责"切与合并"，两者通过该结构解耦。</p>
 */
public record Section(String title, int level, String text) {
}
