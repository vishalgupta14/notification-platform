package com.message.node;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {
		"com.notification.common",
		"com.message.node"
})
public class MessageNodeExecutorApplication {
	public static void main(String[] args) {
		SpringApplication.run(MessageNodeExecutorApplication.class, args);
	}
}

