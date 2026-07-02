package com.paximum.paxassist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PaxAssistApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaxAssistApplication.class, args);
    }
}
