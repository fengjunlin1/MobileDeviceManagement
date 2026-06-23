package com.mdm.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MdmAssistantApplication {
    public static void main(String[] args) {
        SpringApplication.run(MdmAssistantApplication.class, args);
    }
}
