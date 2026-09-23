package com.facultyconnect.feculty_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableMongoAuditing
public class FecultyBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(FecultyBackendApplication.class, args);
	}

}
