#!/bin/bash

echo "=== BaseBall LOCK 크롤러 실행 ==="
echo "현재 시간: $(date)"
echo ""

# Spring Boot 애플리케이션 빌드
echo "1. 프로젝트 빌드 중..."
./gradlew build -x test

if [ $? -eq 0 ]; then
    echo "✅ 빌드 완료"
else
    echo "❌ 빌드 실패"
    exit 1
fi

echo ""
echo "2. 크롤러 실행 중..."

# WAR 파일로 크롤러 실행
java -jar build/libs/BaseBallLOCK-0.0.1-SNAPSHOT.war \
     --spring.main.web-application-type=none \
     --spring.main.sources=com.kepg.BaseBallLOCK.crawler.util.CrawlersManualRunner

echo ""
echo "=== 크롤러 실행 완료 ==="
