package com.sillybilly.eurekaconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class EurekaconfigApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaconfigApplication.class, args);
    }

}
