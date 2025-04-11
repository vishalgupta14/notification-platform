package com.message.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

@SpringBootApplication
@EnableReactiveMongoRepositories(basePackages = "com.notification.common.repository")
@ComponentScan(basePackages = {
		"com.notification.common.service",
		"com.message.engine"
})
public class EmailSenderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(EmailSenderServiceApplication.class, args);
	}

}
