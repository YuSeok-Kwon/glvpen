---
name: baseball-lock
description: This is a new rule
---

# Overview

•실제 야구 경기 데이터를 Statiz에서 크롤링(Selenium + Jsoup)하여 MySQL DB에 저장
•크롤링 데이터 기반으로 시뮬레이션 게임을 실행하고 결과(MVP, 점수, 하이라이트, 로그 등) 자동 생성
•게임 결과를 DTO 구조로 가공하여 클라이언트에 전달 및 뷰 렌더링
•퀴즈 이벤트 기능 (랜덤 5문항 선택, 사용자 응답 저장)
•현재 기술 스택:
•Backend: Spring Boot (Layered Architecture, RESTful API, Lombok, DTO 중심 설계)
•Database: MySQL 5 (Native SQL, camelCase 네이밍)
•Crawler: Selenium + Jsoup (Statiz 연동)
•Infra: AWS EC2 + Tomcat 배포
•Frontend: Thymeleaf 기반 결과 뷰
•설계 원칙:
•Entity 직접 접근 금지 → DTO 기반 처리
•하드코딩 최소화, 타입 안정성 확보 (컴파일 시 에러 검출 중심)
•Decoupling(모듈화) / Monolith 구조 지양
•The Twelve-Factor App 원칙 적용 중
