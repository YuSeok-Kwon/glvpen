package com.kepg.glvpen.crawler.util;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 크롤링 로그인을 중앙 관리하는 컴포넌트.
 * 모든 크롤러가 이 매니저를 통해 인증된 WebDriver를 받는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerLoginManager {

    private static final String LOGIN_URL = "https://www.statiz.co.kr/member/?m=login";

    @Value("${crawler.login.username}")
    private String username;

    @Value("${crawler.login.password}")
    private String password;

    /**
     * WebDriver로 외부 사이트 로그인 수행
     */
    public void login(WebDriver driver) {
        try {
            driver.get(LOGIN_URL);
            Thread.sleep(2000);

            WebElement idField = driver.findElement(By.id("userID"));
            WebElement pwField = driver.findElement(By.id("userPassword"));

            idField.clear();
            idField.sendKeys(username);
            pwField.clear();
            pwField.sendKeys(password);

            // loginCheck() 호출 (로그인 폼의 JS 함수)
            ((JavascriptExecutor) driver).executeScript("loginCheck()");
            Thread.sleep(3000);

            // 로그인 성공 확인
            if (driver.getCurrentUrl().contains("/member/?m=login")) {
                throw new RuntimeException("크롤러 로그인 실패");
            }
            log.info("크롤러 로그인 성공");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("크롤러 로그인 중 인터럽트 발생", e);
        }
    }

    /**
     * 로그인 상태 확인 (페이지 로드 후 리다이렉트 감지)
     */
    public boolean isLoggedIn(WebDriver driver) {
        return !driver.getPageSource().contains("로그인 후 이용 가능합니다");
    }
}
