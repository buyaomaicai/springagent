package com.springagent.diagnosis.domain.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Data
public class DiagnosisParserRequest {
    private UUID conversationId;
    @NotBlank(message = "诊断内容不能为空")
    private String input;
    String fileName;
    String mediaType;;
    @NotNull(message = "POM 文件不能为空")
    private MultipartFile file;

    @AssertTrue(message = "POM 文件不能为空")
    public boolean isFilePresent() {
        return file != null && !file.isEmpty();
    }
}
