package com.springagent.diagnosis.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springagent.common.Constant.SenderRole;
import com.springagent.diagnosis.entity.ChatMessage;
import com.springagent.diagnosis.model.DiagnosisPromptContext;
import com.springagent.diagnosis.model.ProjectDependency;
import com.springagent.diagnosis.model.ProjectInput;
import com.springagent.diagnosis.model.VersionSource;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;

class DiagnosisPromptBuilderTests {

    private DiagnosisPromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        ProjectContextFormatter formatter =
                new ProjectContextFormatter(new ObjectMapper());
        promptBuilder = new DiagnosisPromptBuilder(
                formatter,
                new ClassPathResource("prompts/diagnosis/system.st"),
                new ClassPathResource("prompts/diagnosis/user.st")
        );
    }

    @Test
    void includesStructuredProjectContextAndReferences() {
        ProjectDependency starterWeb = new ProjectDependency(
                "org.springframework.boot",
                "spring-boot-starter-web",
                "3.2.5",
                "compile",
                "jar",
                false,
                VersionSource.SPRING_BOOT_BOM,
                List.of()
        );
        ProjectInput projectInput = new ProjectInput(
                "com.example",
                "upgrade-demo",
                "1.0.0",
                "jar",
                "17",
                "3.2.5",
                List.of(starterWeb),
                List.of(),
                List.of(),
                List.of("检测到 Maven Profile")
        );
        DiagnosisPromptContext context = new DiagnosisPromptContext(
                "如何升级到 Spring Boot 3.4？",
                List.of(historyMessage(
                        SenderRole.USER,
                        "这是上一轮问题"
                )),
                List.of(new Document("Spring Boot 3.4 migration guide")),
                Optional.of(projectInput)
        );

        Prompt prompt = promptBuilder.build(context);

        List<Message> messages = prompt.getInstructions();
        assertEquals(3, messages.size());
        assertInstanceOf(SystemMessage.class, messages.get(0));
        assertInstanceOf(UserMessage.class, messages.get(1));
        assertInstanceOf(UserMessage.class, messages.get(2));

        UserMessage currentQuestion =
                assertInstanceOf(UserMessage.class, messages.get(2));
        String text = currentQuestion.getText();
        assertTrue(text.contains("<project_context>"));
        assertTrue(text.contains("\"artifactId\" : \"upgrade-demo\""));
        assertTrue(text.contains("\"javaVersion\" : \"17\""));
        assertTrue(text.contains("\"springBootVersion\" : \"3.2.5\""));
        assertTrue(text.contains("spring-boot-starter-web"));
        assertTrue(text.contains("SPRING_BOOT_BOM"));
        assertTrue(text.contains("如何升级到 Spring Boot 3.4？"));
        assertTrue(text.contains("<reference>"));
        assertTrue(text.contains("Spring Boot 3.4 migration guide"));
        assertFalse(text.contains("\"status\": \"NOT_PROVIDED\""));
        assertFalse(text.contains("$projectContext$"));

        String systemText = assertInstanceOf(
                SystemMessage.class,
                messages.get(0)
        ).getText();
        assertTrue(
                systemText.contains("只能输出一个合法 JSON 对象"),
                systemText
        );
        assertTrue(systemText.contains("\"compatibilityIssues\""));
        assertTrue(systemText.contains("LOW | MEDIUM | HIGH | CRITICAL"));
        assertTrue(systemText.contains(
                "PREPARATION | BUILD | SOURCE_CODE | DATA | TESTING"
        ));
    }

    @Test
    void marksProjectContextAsNotProvidedAndPreservesHistoryOrder() {
        DiagnosisPromptContext context = new DiagnosisPromptContext(
                "通用升级建议是什么？",
                List.of(
                        historyMessage(SenderRole.USER, "第一轮问题"),
                        historyMessage(SenderRole.ASSISTANT, "第一轮回答")
                ),
                List.of(),
                Optional.empty()
        );

        Prompt prompt = promptBuilder.build(context);

        List<Message> messages = prompt.getInstructions();
        assertEquals(4, messages.size());
        assertInstanceOf(SystemMessage.class, messages.get(0));
        assertEquals(
                "第一轮问题",
                assertInstanceOf(UserMessage.class, messages.get(1))
                        .getText()
        );
        assertEquals(
                "第一轮回答",
                assertInstanceOf(AssistantMessage.class, messages.get(2))
                        .getText()
        );

        String currentQuestion = assertInstanceOf(
                UserMessage.class,
                messages.get(3)
        ).getText();
        assertTrue(currentQuestion.contains("\"status\": \"NOT_PROVIDED\""));
        assertTrue(currentQuestion.contains("通用升级建议是什么？"));
    }

    private ChatMessage historyMessage(
            SenderRole senderRole,
            String content
    ) {
        ChatMessage message = new ChatMessage();
        message.setSenderRole(senderRole);
        message.setContent(content);
        return message;
    }
}
