# 프로젝트 문서

이 디렉토리는 BaseBall LOCK 프로젝트의 모든 문서를 포함합니다.

## 디렉토리 구조

### planning/
게임 기획 및 초기 설계 문서

- `BaseBall LOCK 2.md`: 프로젝트 기본 기획서
- `BaseBall LOCK2 Game.md`: 게임 모드 상세 기획
- `야구_시뮬레이션_웹앱_기획서.docx`: 초기 웹앱 기획서
- `CustomHitterMode_Plan.md`: 커스텀 타자 모드 상세 기획
- `BaseBallLOCK_GameSummary.docx`: 게임 요약 문서
- `GOOGLE_STITCH_DESIGN_PROMPT.md`: Google Stitch 디자인 프롬프트

### architecture/
시스템 아키텍처 및 설계 문서

- `baseball-lock-animation-design.md`: 애니메이션 시스템 설계
- `baseball-lock-auth-system.md`: 인증 시스템 설계 (BCrypt, JWT)
- `baseball-lock-feature-structure.md`: Feature-based 구조 설계
- `RESTFUL_API_GUIDE.md`: RESTful API 설계 가이드

### database/
데이터베이스 스키마 및 설계 문서

- `DATABASE_STRUCTURE.md`: DB 구조 상세 문서
- `BaseBallLock 테이블 관계도 (ERD 스타일).png`: ERD 다이어그램
- `BaseBallLock_DB_설계표_부분.xlsx`: DB 설계 스프레드시트
- `테이블 관계도 요약.csv`: 테이블 관계 요약

### reports/
개발 진행 리포트 및 에러 분석

- `API_IMPLEMENTATION_REPORT.md`: API 구현 상태 리포트
- `ERROR_CHECK_REPORT.md`: 에러 체크 리포트
- `FINAL_SUMMARY.md`: 프로젝트 최종 요약
- `MISSING_TEMPLATES.md`: 누락된 템플릿 목록
- `RUNTIME_ERRORS_REPORT.md`: 런타임 에러 리포트
- `DEVELOPMENT_ROADMAP.md`: 개발 로드맵

### migration/
데이터 마이그레이션 가이드

- `MIGRATION_GUIDE.md`: 비밀번호 암호화 마이그레이션 가이드

### references/
참고 자료 및 외부 문서

- `KBO 타자 OPS 예측 프로젝트 최종발표.pdf`: 관련 프로젝트 발표 자료

## 문서 활용 가이드

### 프로젝트 이해하기

1. **프로젝트 개요**: `planning/BaseBall LOCK 2.md`에서 시작
2. **게임 모드 이해**: `planning/CustomHitterMode_Plan.md` 참조
3. **시스템 구조**: `architecture/baseball-lock-feature-structure.md` 참조

### API 개발

1. **API 설계 원칙**: `architecture/RESTFUL_API_GUIDE.md` 확인
2. **인증 구현**: `architecture/baseball-lock-auth-system.md` 참조

### 데이터베이스 작업

1. **테이블 구조**: `database/DATABASE_STRUCTURE.md` 확인
2. **관계 파악**: `database/BaseBallLock 테이블 관계도 (ERD 스타일).png` 참조
3. **마이그레이션**: `migration/MIGRATION_GUIDE.md` 따르기

### UI/UX 개발

1. **애니메이션 구현**: `architecture/baseball-lock-animation-design.md` 참조
2. **디자인 가이드**: `planning/GOOGLE_STITCH_DESIGN_PROMPT.md` 활용

## 문서 업데이트 규칙

### 새 문서 추가 시

1. 적절한 카테고리 폴더에 배치
2. 문서 제목은 명확하고 일관된 네이밍 사용
3. 이 README.md에 새 문서 추가 사항 업데이트

### 문서 수정 시

1. 변경 이력을 문서 하단에 기록
2. 중요한 변경사항은 Git 커밋 메시지에 명시

### 네이밍 규칙

- 영문 문서: `UPPER_CASE.md` 또는 `kebab-case.md`
- 한글 문서: `명확한_설명.docx` 또는 `.md`
- 이미지: `설명적인_이름.png`

## 문서 작성 가이드

### Markdown 문서

- 제목은 `#`로 시작
- 코드 블록은 언어 지정 (```java, ```sql 등)
- 테이블은 가독성 있게 정렬
- 링크는 상대 경로 사용

### 다이어그램

- ERD는 PNG 형식으로 저장
- 해상도는 최소 1920x1080
- 파일명에 버전 포함 (선택사항)

## 관련 링크

- [프로젝트 루트 README](../README.md)
- [개발 가이드 (CLAUDE.md)](../CLAUDE.md)
