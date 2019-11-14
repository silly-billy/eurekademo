package com.example.demo.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
public class WebMVCConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        //将 /front/src/login/** 的请求 映射到 /front/src/login/下
        /*registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
*/
       /* registry.addResourceHandler("swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");

        registry.addResourceHandler("/templates/**")
                .addResourceLocations("classpath:/META-INF/resources/templates/");*/
    }

}
