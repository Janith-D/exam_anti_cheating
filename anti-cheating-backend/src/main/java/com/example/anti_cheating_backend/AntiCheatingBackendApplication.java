package com.example.anti_cheating_backend;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;

@SpringBootApplication
@EnableWebSocketMessageBroker
public class AntiCheatingBackendApplication {

	@Autowired
	private HikariDataSource dataSource;

	public static void main(String[] args) {
		SpringApplication.run(AntiCheatingBackendApplication.class, args);
	}
	@PreDestroy
	public void onExit(){
		if(dataSource != null){
			dataSource.close();
		}
	}

}
