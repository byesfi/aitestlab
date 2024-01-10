package com.byesfi.aitestlab;

import org.springframework.ai.chat.ChatClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AiTestLabApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiTestLabApplication.class, args);
	}

	@Bean
	ApplicationRunner applicationRunner(ChatClient chatClient){
		return new ApplicationRunner() {
			@Override
			public void run(ApplicationArguments args) throws Exception {
					var template = chatClient.generate("Who are you?");
				System.out.println("response: " + template);
			}
		};
	}
}
