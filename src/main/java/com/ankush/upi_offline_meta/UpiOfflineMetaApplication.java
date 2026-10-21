package com.ankush.upi_offline_meta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UpiOfflineMetaApplication {

	public static void main(String[] args) {
		SpringApplication.run(UpiOfflineMetaApplication.class, args);
	}

}
