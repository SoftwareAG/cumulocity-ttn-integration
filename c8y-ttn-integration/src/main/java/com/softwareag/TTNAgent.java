package com.softwareag;

import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.ComponentScan;

import com.cumulocity.microservice.autoconfigure.MicroserviceApplication;

@MicroserviceApplication
@ComponentScan(basePackages = { "com.softwareag", "com.cumulocity.lpwan.devicetype.service" })
public class TTNAgent {
	public static void main(String[] args) {
		SpringApplication.run(TTNAgent.class, args);
	}
}
