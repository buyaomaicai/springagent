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

    /**
     * 把检索到的文档渲染成带编号的引用块 [REF-i]，并附上来源标题与 URL，
     * 使模型可以在 evidence 中按 refIndex 精确引用，而不是凭记忆编造来源。
     */
    private String buildReferences(List<Document> references) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < references.size(); index++) {
            Document reference = references.get(index);
            builder.append("<reference id=\"REF-")
                    .append(index)
                    .append("\">\n");
            String title = referenceTitle(reference);
            if (!title.isEmpty()) {
                builder.append("<title>")
                        .append(title)
                        .append("</title>\n");
            }
            String url = referenceUrl(reference);
            if (!url.isEmpty()) {
                builder.append("<source_url>")
                        .append(url)
                        .append("</source_url>\n");
            }
            builder.append("<content>\n")
                    .append(reference.getText())
                    .append("\n</content>\n")
                    .append("</reference>\n");
        }
        return builder.toString();
    }

    /**
     * 优先使用文档元数据中的 source_id，否则取正文第一行作为标题。
     */
    private String referenceTitle(Document reference) {
        Object sourceId = reference.getMetadata().get("source_id");
        if (sourceId != null && !sourceId.toString().isBlank()) {
            return sourceId.toString();
        }
        String firstLine = reference.getText().lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse("Unnamed reference");
        return firstLine.length() <= 120
                ? firstLine
                : firstLine.substring(0, 120) + "...";
    }

    private String referenceUrl(Document reference) {
        Object url = reference.getMetadata().get("source_url");
        return url == null ? "" : url.toString();
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
