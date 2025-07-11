# 🧱 BaseBall LOCK 프로젝트 기준: Feature-based 구조 장단점 정리

BaseBall LOCK 리팩토링에서 사용 중인 **Feature-based 구조 (도메인 중심 폴더 구조)**에 대한 장단점을  
현재 프로젝트 특성에 맞춰 분석한 내용입니다.

---

## 📌 전제: BaseBall LOCK 프로젝트 특성

- 유저, 게임, 리뷰, 카드 등 **도메인(기능) 복잡도 높음**
- RESTful API 사용
- 모듈화 / Decoupling / 테스트 중시
- Monolith 지양 + 확장 고려
- Twelve-Factor App 원칙 적용
- TypeScript + Node.js 백엔드

---

## ✅ Feature-based 구조란?

- 기능(도메인) 단위로 폴더 분리  
- 각 폴더 안에 controller, service, repository, model 등 포함  
- 흔히 `Vertical Slice`, `도메인 중심`, `모듈 중심 구조`라고도 불림

### 📁 예시
```
/modules
  ├── user/
  │   ├── user.controller.ts
  │   ├── user.service.ts
  │   ├── user.repository.ts
  │   ├── user.dto.ts
  └── game/
      ├── game.controller.ts
      ├── game.service.ts
```

---

## ✅ 장점 (BaseBall LOCK 기준)

| 항목 | 설명 |
|------|------|
| **📦 관심사 집중 (Cohesion)** | 관련 로직이 도메인별로 모여 있어서 변경 시 추적 쉬움 |
| **🧪 테스트 용이성** | 단위 테스트를 모듈 단위로 작성 가능 (mock 쉽게 가능) |
| **🔧 확장성 우수** | 새로운 도메인(`match`, `ranking`, `shop`) 추가 시 폴더 하나로 확장 |
| **🧹 유지보수 쉬움** | 특정 기능 수정 시 해당 도메인 폴더만 보면 됨 |
| **🧱 마이크로서비스로 전환 용이** | 모듈 단위로 떼어내기 쉬움 |
| **👥 협업에 유리** | 업무 분배를 도메인 단위로 나누기 쉬움 |
| **📁 파일 수 분산** | controller/, service/ 같은 폴더에 파일 몰리지 않음 |

---

## ⚠️ 단점 (하지만 대부분 해결 가능)

| 항목 | 설명 | 해결 방법 |
|------|------|-------------|
| **초기 설계 복잡도** | 폴더 구조 설계 시간 필요 | ✅ 현재 충분히 구조 잡는 중 |
| **간단한 기능엔 과한 구조** | 로그인만 해도 3~4개 파일 생성 | ✅ BaseBall LOCK은 단순하지 않음 |
| **반복 구조** | controller/service/repo 반복됨 | ✅ CLI 코드 생성기로 자동화 가능 (ex: Plop.js) |

---

## 🔥 결론

> **BaseBall LOCK 같은 기능 중심 + 테스트 지향 + 확장 가능한 프로젝트에는 Feature-based 구조가 Best Fit이다.**

- 기능 추가/변경/삭제에 유연
- 추후 마이크로서비스나 서버 분리에도 유리
- 단위 테스트, 관심사 분리에 강함

---

## ✨ 다음 단계 제안

- CLI 코드 생성기 적용 (ex. Plop.js)
- 기능 모듈 템플릿 정리
- 공통 유틸/에러 미들웨어 구조 도입