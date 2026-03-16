//package com.kepg.glvpen.config;
//
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
//import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
//
//@Configuration
//public class WebConfig implements WebMvcConfigurer {
//
//    @Override
//    public void addResourceHandlers(ResourceHandlerRegistry registry) {
//        registry.addResourceHandler("/static/**") // URL 매칭
//                .addResourceLocations("classpath:/static/") // 파일 위치
//                .setCachePeriod(3600) // 캐시 설정
//                .resourceChain(true);
//    }
//}