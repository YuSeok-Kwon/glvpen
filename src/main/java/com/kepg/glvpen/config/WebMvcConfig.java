package com.kepg.glvpen.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginCheckInterceptor())
                .addPathPatterns("/ranking/**", "/schedule/**", "/game/**", "/analysis/**",
                        "/user/**", "/simulations/**") // 보호할 경로
                .excludePathPatterns(
                        "/user/login-view", "/user/login", "/user/join", "/user/join-view",
                        "/user/find-id-view", "/user/find-password-view",
                        "/user/duplicate-id", "/user/duplicate-nickname",
                        "/css/**", "/js/**", "/img/**", "/static/**"
                ); // 로그인 없이 접근 허용
    }
}