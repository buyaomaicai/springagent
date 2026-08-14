package com.springagent.parser.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.springagent.common.exception.GlobalExceptionHandler;
import com.springagent.common.web.RequestIdFilter;
import com.springagent.parser.bom.ResolvedSpringBootBom;
import com.springagent.parser.impl.PomXmlParser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProjectArtifactControllerTests {

    private static final String REQUEST_ID =
            "project-artifact-test-request";
    private static final int MAX_POM_SIZE = 1024 * 1024;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Controller 测试不应依赖开发机真实 .m2 内容，因此使用一个确定性的空 BOM 结果。
        PomXmlParser parser = new PomXmlParser(
                version -> new ResolvedSpringBootBom(
                        version,
                        true,
                        Map.of(),
                        List.of()
                )
        );
        ProjectArtifactController controller =
                new ProjectArtifactController(parser);

        // standaloneSetup 只装配该接口需要的 MVC 组件，不会启动数据库、DeepSeek 或 Ollama。
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void parsesUploadedPomIntoStandardResponse() throws Exception {
        MockMultipartFile file = pomFile("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>upload-demo</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <java.version>17</java.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>com.example</groupId>
                            <artifactId>example-library</artifactId>
                            <version>2.1.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        mockMvc.perform(multipart("/project-artifacts/pom")
                        .file(file)
                        .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        RequestIdFilter.HEADER_NAME,
                        REQUEST_ID
                ))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.data.groupId")
                        .value("com.example"))
                .andExpect(jsonPath("$.data.artifactId")
                        .value("upload-demo"))
                .andExpect(jsonPath("$.data.javaVersion").value("17"))
                .andExpect(jsonPath("$.data.dependencies[0].version")
                        .value("2.1.0"))
                .andExpect(jsonPath(
                        "$.data.dependencies[0].versionSource"
                ).value("DECLARED"));
    }

    @Test
    void returnsBadRequestForMalformedPom() throws Exception {
        MockMultipartFile file = pomFile("""
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>broken
                </project>
                """);

        mockMvc.perform(multipart("/project-artifacts/pom")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("PROJECT_ARTIFACT_INVALID"))
                .andExpect(jsonPath("$.message")
                        .value("POM XML 格式错误"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void returnsBadRequestForUnsafeXml() throws Exception {
        MockMultipartFile file = pomFile("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE project [
                    <!ENTITY localFile SYSTEM "file:///etc/passwd">
                ]>
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>&localFile;</artifactId>
                </project>
                """);

        mockMvc.perform(multipart("/project-artifacts/pom")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("PROJECT_ARTIFACT_INVALID"))
                .andExpect(jsonPath("$.message")
                        .value("POM XML 不允许包含 DOCTYPE 或外部实体"));
    }

    @Test
    void returnsPayloadTooLargeForOversizedPom() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "pom.xml",
                MediaType.APPLICATION_XML_VALUE,
                new byte[MAX_POM_SIZE + 1]
        );

        mockMvc.perform(multipart("/project-artifacts/pom")
                        .file(file))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code")
                        .value("PROJECT_ARTIFACT_TOO_LARGE"))
                .andExpect(jsonPath("$.message")
                        .value("POM XML 不能超过 1 MB"));
    }

    @Test
    void returnsFieldErrorWhenFilePartIsMissing() throws Exception {
        mockMvc.perform(multipart("/project-artifacts/pom"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field")
                        .value("file"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("缺少必要文件"));
    }

    private MockMultipartFile pomFile(String xml) {
        return new MockMultipartFile(
                "file",
                "pom.xml",
                MediaType.APPLICATION_XML_VALUE,
                xml.getBytes(StandardCharsets.UTF_8)
        );
    }
}
