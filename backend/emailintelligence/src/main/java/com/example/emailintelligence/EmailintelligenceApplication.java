package com.example.emailintelligence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class EmailintelligenceApplication {

	public static void main(String[] args) {
		SpringApplication.run(EmailintelligenceApplication.class, args);
	}

}
