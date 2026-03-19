package com.kepg.glvpen.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 보안 관련 설정 클래스
 * BCrypt 비밀번호 암호화를 위한 PasswordEncoder Bean 제공
 */
@Configuration
public class SecurityConfig {

    /**
     * BCrypt 암호화를 사용하는 PasswordEncoder Bean
     *
     * BCrypt는 해시 함수에 salt를 자동으로 추가하며,
     * 레인보우 테이블 공격에 강력한 보안성을 제공합니다.
     *
     * @return BCryptPasswordEncoder 인스턴스
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
