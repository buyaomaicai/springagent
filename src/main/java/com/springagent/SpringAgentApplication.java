package com.springagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.springagent.diagnosis.mapper")
public class SpringAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAgentApplication.class, args);
    }
}
