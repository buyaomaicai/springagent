package com.springagent;

import com.springagent.knowledge.service.KnowledgeIngestionService;
import com.springagent.knowledge.service.KnowledgeRetrievalService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.List;

@SpringBootTest

public class RagTests {
    @Autowired
    KnowledgeRetrievalService knowledgeRetrievalService;
    @Autowired
    KnowledgeIngestionService knowledgeIngestionService;
    @Test
    public void testKnowledge(){
        knowledgeIngestionService.ingestSpringBoot30Guide();
        List<Document> spring = knowledgeRetrievalService.searchSpringBoot30("How to migrate from javax to jakarta in Spring Boot 3.0");
        for (Document d : spring){
            System.out.println(d.getText());
        }
    }
}
