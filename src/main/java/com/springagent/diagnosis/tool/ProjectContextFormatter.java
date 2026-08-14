package com.springagent.diagnosis.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springagent.diagnosis.model.ProjectInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectContextFormatter {

    private final ObjectMapper objectMapper;

    public String format(ProjectInput projectInput) {
        try {
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(projectInput);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "序列化项目上下文失败",
                    exception
            );
        }
    }

}