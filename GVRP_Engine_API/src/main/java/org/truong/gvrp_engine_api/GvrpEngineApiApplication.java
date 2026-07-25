package org.truong.gvrp_engine_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class GvrpEngineApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GvrpEngineApiApplication.class, args);
    }

}
