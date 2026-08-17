package com.springagent.diagnosis.tool;

import com.springagent.diagnosis.model.DiagnosisPromptContext;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DiagnosisPromptBuilder {

    private static final TemplateRenderer TEMPLATE_RENDERER =
            StTemplateRenderer.builder()
                    .startDelimiterToken('$')
                    .endDelimiterToken('$')
                    .build();

    private final ProjectContextFormatter projectContextFormatter;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;

    public DiagnosisPromptBuilder(
            ProjectContextFormatter projectContextFormatter,
            @Value("classpath:prompts/diagnosis/system.st")
            Resource systemPromptResource,
            @Value("classpath:prompts/diagnosis/user.st")
            Resource userPromptResource
    ) {
        this.projectContextFormatter = projectContextFormatter;
        this.systemPromptTemplate = promptTemplate(systemPromptResource);
        this.userPromptTemplate = promptTemplate(userPromptResource);
    }

    public Prompt build(DiagnosisPromptContext context) {
        List<Message> messages = new ArrayList<>();

        messages.add(new SystemMessage(systemPromptTemplate.render()));

        messages.addAll(context.history().stream()
                .map(message -> message.getSenderRole()
                        .toAiMessage(message.getContent()))
                .toList());

        messages.add(new UserMessage(
                buildCurrentQuestion(context)
        ));

        return new Prompt(messages);
    }

    private String buildCurrentQuestion(
            DiagnosisPromptContext context
    ) {
        String projectSection = context.projectInput()
                .map(projectContextFormatter::format)
                .orElse("""
                        {
                          "status": "NOT_PROVIDED"
                        }
                        """);

        return userPromptTemplate.render(Map.of(
                "projectContext", projectSection,
                "userQuestion", context.question(),
                "references", buildReferences(context.references())
        ));
    }

    private String buildReferences(List<Document> references) {
        return references.stream()
                .map(reference -> """
                        <reference>
                        %s
                        </reference>
                        """.formatted(reference.getText()))
                .collect(Collectors.joining("\n"));
    }

    private PromptTemplate promptTemplate(Resource resource) {
        try {
            String template = resource.getContentAsString(
                    StandardCharsets.UTF_8
            );
            return PromptTemplate.builder()
                    .template(template)
                    .renderer(TEMPLATE_RENDERER)
                    .build();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "无法读取诊断 Prompt 模板: "
                            + resource.getDescription(),
                    exception
            );
        }
    }

}
