package org.truong.gvrp_engine_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class GvrpEngineApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GvrpEngineApiApplication.class, args);
    }

}
