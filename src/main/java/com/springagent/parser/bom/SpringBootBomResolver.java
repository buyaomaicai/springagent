package com.springagent.parser.bom;

/**
 * 根据 Spring Boot 版本获取其 BOM 中直接管理的依赖版本。
 */
public interface SpringBootBomResolver {

    ResolvedSpringBootBom resolve(String springBootVersion);
}
