package com.springagent.parser;

/**
 * 项目诊断支持的输入材料类型。
 */
public enum ArtifactType {

    /**
     * Maven 项目描述文件。
     */
    POM_XML,

    /**
     * Maven 或 Gradle 依赖列表。
     */
    DEPENDENCY_LIST,

    /**
     * 编译、测试或应用运行错误日志。
     */
    ERROR_LOG,

    /**
     * 暂未归类的其他项目材料。
     */
    OTHER
}