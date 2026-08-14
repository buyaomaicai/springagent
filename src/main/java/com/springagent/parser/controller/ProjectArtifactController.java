package com.springagent.parser.controller;

import com.springagent.common.api.ApiResponse;
import com.springagent.diagnosis.model.ProjectInput;
import com.springagent.parser.ArtifactType;
import com.springagent.parser.exception.ProjectArtifactParseException;
import com.springagent.parser.impl.PomXmlParser;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 接收用户上传的项目材料，并将其转换为诊断模块能够直接使用的结构化数据。
 *
 * <p>当前只开放 Maven {@code pom.xml} 的解析接口。文件上传和 POM 解析被设计成独立于 SSE
 * 诊断的同步操作：前端可以先展示解析结果和 warning，让用户确认项目版本信息，再发起耗时更长的
 * 大模型诊断。</p>
 */
@RestController
@RequestMapping("/project-artifacts")
@RequiredArgsConstructor
public class ProjectArtifactController {

    /**
     * POM 的格式、安全和大小规则由专用 Parser 统一负责，Controller 不重复实现这些规则。
     */
    private final PomXmlParser pomXmlParser;

    /**
     * 解析一个通过 multipart/form-data 上传的 Maven POM。
     *
     * <p>请求中的文件字段名固定为 {@code file}。MultipartFile 只属于当前请求，因此这里使用
     * try-with-resources 及时关闭输入流；Parser 按约定不会替 Controller 关闭调用方传入的流。</p>
     *
     * @param file 用户上传的 pom.xml，字段名必须是 file
     * @return 包含项目坐标、Java/Spring Boot 版本、直接依赖和解析 warning 的统一响应
     * @throws ProjectArtifactParseException 文件为空、过大、格式错误、不安全或读取失败时抛出
     */
    @PostMapping(
            value = "/pom",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ApiResponse<ProjectInput> parsePom(
            @RequestPart("file") MultipartFile file
    ) {
        // 即使 MultipartFile 对象存在，零字节文件也应交给 Parser 产生统一的 EMPTY_CONTENT 分类。
        try (InputStream input = file.getInputStream()) {
            ProjectInput projectInput = pomXmlParser.parse(input);
            return ApiResponse.success(projectInput);
        } catch (ProjectArtifactParseException exception) {
            // 领域异常已经携带稳定 Reason，保持原样交给全局异常处理器进行 HTTP 映射。
            throw exception;
        } catch (IOException exception) {
            // MultipartFile 在打开或关闭底层临时文件时都可能产生 I/O 异常，统一转成解析领域异常。
            throw new ProjectArtifactParseException(
                    ArtifactType.POM_XML,
                    ProjectArtifactParseException.Reason.IO_ERROR,
                    "读取上传的 POM 文件失败",
                    exception
            );
        }
    }
}
