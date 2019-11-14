package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;


@EnableTransactionManagement
@ComponentScan(basePackages = { "com.example.demo.core.common", "com.example.demo.core.service","com.example.demo.core.dal","com.example.demo.web"
,"com.example.demo.web.config"})
@MapperScan(basePackages = {"com.example.demo.core.dal.dao"})
@SpringBootApplication(exclude = {HibernateJpaAutoConfiguration.class},scanBasePackages = "com.example.demo")
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

}
