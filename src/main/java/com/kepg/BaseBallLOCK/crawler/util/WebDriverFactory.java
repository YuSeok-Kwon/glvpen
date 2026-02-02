package com.kepg.BaseBallLOCK.crawler.util;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * WebDriver 생성을 담당하는 팩토리 클래스
 * 모든 크롤러에서 일관된 WebDriver 설정을 사용할 수 있도록 합니다.
 */
public final class WebDriverFactory {

    private WebDriverFactory() {
        throw new AssertionError("유틸리티 클래스는 인스턴스화할 수 없습니다");
    }

    /**
     * 기본 설정으로 ChromeDriver를 생성합니다.
     * - headless 모드
     * - sandbox 비활성화
     * - dev-shm-usage 비활성화
     *
     * @return 설정된 WebDriver 인스턴스
     */
    public static WebDriver createChromeDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
            "--headless",
            "--no-sandbox",
            "--disable-dev-shm-usage"
        );
        return new ChromeDriver(options);
    }

    /**
     * 추가 옵션을 포함한 ChromeDriver를 생성합니다.
     * - headless 모드
     * - sandbox 비활성화
     * - dev-shm-usage 비활성화
     * - GPU 비활성화
     * - remote-allow-origins 허용
     *
     * @return 설정된 WebDriver 인스턴스
     */
    public static WebDriver createChromeDriverWithExtendedOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
            "--headless",
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--disable-gpu",
            "--remote-allow-origins=*"
        );
        return new ChromeDriver(options);
    }

    /**
     * 커스텀 ChromeOptions로 ChromeDriver를 생성합니다.
     *
     * @param options 커스텀 ChromeOptions
     * @return 설정된 WebDriver 인스턴스
     */
    public static WebDriver createChromeDriver(ChromeOptions options) {
        return new ChromeDriver(options);
    }

    /**
     * 기본 ChromeOptions를 생성합니다.
     * 필요에 따라 추가 설정을 할 수 있습니다.
     *
     * @return 기본 설정이 적용된 ChromeOptions
     */
    public static ChromeOptions createDefaultChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
            "--headless",
            "--no-sandbox",
            "--disable-dev-shm-usage"
        );
        return options;
    }
}
