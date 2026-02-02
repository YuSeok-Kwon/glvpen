# BaseBall LOCK 2.0 - Google Stitch 디자인 프롬프트

---

## 📋 프로젝트 개요

**BaseBall LOCK 2.0**은 KBO 야구 시뮬레이션 게임 웹 애플리케이션입니다. 실제 경기 데이터를 기반으로 하는 시뮬레이션, 커스텀 타자 육성, 클래식 대전 모드를 제공하며, 야구 팬들이 몰입감 있는 경험을 할 수 있도록 설계되었습니다.

### 핵심 타겟
- KBO 야구 팬
- 시뮬레이션 게임 애호가
- 데이터 분석에 관심있는 사용자
- 모바일/데스크톱 모든 기기 사용자

---

## 🎨 디자인 시스템

### 색상 팔레트

#### 메인 브랜드 컬러
```
Primary Yellow (Main Brand): #FACC15
- 메인 액션 버튼
- 로고 텍스트
- 강조 요소
- 네비게이션 언더라인

Secondary Colors:
- Background Gray: #F5F5F5 (페이지 배경)
- White: #FFFFFF (카드, 박스 배경)
- Dark Text: #111111, #333333 (본문 텍스트)
- Light Gray: #EAEAEA (푸터 배경)
```

#### KBO 10개 팀 브랜드 컬러
```
1. 기아 타이거즈: #ED1C24 (빨강)
2. 두산 베어스: #042071 (남색)
3. 삼성 라이온즈: #0061AA (파랑)
4. SSG 랜더스: #CF152D (진한 빨강)
5. LG 트윈스: #FC1CAD (마젠타/핑크)
6. 한화 이글스: #F37321 (주황)
7. NC 다이노스: #002B69 (짙은 남색)
8. KT 위즈: #000000 (검정)
9. 롯데 자이언츠: #888888 (회색)
10. 키움 히어로즈: #86001F (버건디)
```

**사용처**: 팀별 배경색, 텍스트 강조, 경기 결과 표시, 리뷰 캘린더, 카드 구분

#### 기능별 색상
```
Success (승리): #28A745 (Green)
Danger (패배): #DC3545 (Red)
Warning (무승부): #FFC107 (Amber)
Info: #17A2B8 (Cyan)

Gradient Colors:
- Purple Gradient: linear-gradient(135deg, #667eea 0%, #764ba2 100%)
- Red Gradient: linear-gradient(135deg, #e74c3c 0%, #c0392b 100%)
- Orange Gradient: linear-gradient(135deg, #ffd700, #ff9800)
- Green Gradient: linear-gradient(135deg, #28a745, #20c997)
```

---

### 타이포그래피

#### 폰트 패밀리
```css
/* 로고/타이틀 전용 */
font-family: 'Dokdo', cursive;
- 사용처: "BaseBall LOCK" 로고
- 크기: 90px~100px (반응형)
- 효과: 검은색 외곽선 (-2px~2px text-shadow)
- 컬러: #FACC15 (노란색)

/* 본문 전용 */
font-family: 'Pretendard', 'Noto Sans KR', sans-serif;
- 기본 크기: 1.1rem (데스크톱)
- 모바일: 0.9rem~1rem
- Line Height: 1.5~1.6
- 사용처: 모든 일반 텍스트, UI 요소
```

#### 타이포그래피 계층
```
H1 (페이지 제목): 2.5rem, font-weight: bold
H2 (섹션 제목): 2rem, font-weight: bold
H3 (카드 제목): 1.5rem, font-weight: bold
H4 (서브 제목): 1.25rem, font-weight: 600
H5 (작은 제목): 1.1rem, font-weight: bold
Body (본문): 1.1rem, font-weight: 400
Small (보조): 0.85rem~0.95rem, font-weight: 400
```

---

### 레이아웃 시스템

#### 그리드 구조
```
Max Width: 1200px (중앙 정렬)
Container Padding:
- Desktop (1201px+): 3rem (좌우)
- Tablet/Mobile (≤1200px): 10px (좌우)

Grid System: Bootstrap 5.1.3 기반
- 12 Column Grid
- Responsive Breakpoints:
  - XS: < 480px (모바일)
  - SM: 480px ~ 768px (태블릿)
  - MD: 768px ~ 1024px (작은 데스크톱)
  - LG: 1024px ~ 1200px (데스크톱)
  - XL: 1200px+ (대형 데스크톱)
```

#### 여백 시스템
```
Spacing Scale:
- xs: 0.25rem (4px)
- sm: 0.5rem (8px)
- md: 1rem (16px)
- lg: 1.5rem (24px)
- xl: 2rem (32px)
- xxl: 3rem (48px)

카드 간격: 10px~15px (반응형)
섹션 간격: 2rem~3rem
```

#### Border Radius
```
Small: 8px (버튼, 인풋)
Medium: 12px~15px (카드, 박스)
Large: 20px (모달, 큰 카드)
Circle: 50% (아바타, 로고)
```

---

## 📱 페이지별 상세 설명

### 1. User 관리 페이지

#### 1.1 로그인 페이지 (`/user/login-view`)
**목적**: 사용자 인증 및 세션 관리

**레이아웃**:
- 중앙 정렬 레이아웃 (flexbox vertical center)
- 배경: 밝은 회색 (#F0F0F0)
- 로고: 상단 중앙 "BaseBall LOCK" (Dokdo 폰트, 90px, 노란색 + 검은 외곽선)

**주요 요소**:
```html
- 로그인 박스 (700px max-width, 흰색 배경, 둥근 모서리 16px)
  ├─ 제목: "로그인" (h1)
  ├─ 아이디 입력 (form-control)
  ├─ 비밀번호 입력 (form-control, type="password")
  ├─ 제출 버튼: "Let's LOCK" (노란색 #FACC15, 전체 너비, fw-bold)
  └─ 링크 영역
      ├─ 아이디 찾기
      ├─ 비밀번호 찾기
      └─ 회원가입 링크 (굵게)
```

**인터랙션**:
- 입력 필드 focus 시 border color 변경 (#667eea)
- 버튼 hover 시 미세한 scale up (1.02)
- AJAX 기반 비동기 로그인 처리

---

#### 1.2 회원가입 페이지 (`/user/join-view`)
**목적**: 신규 사용자 등록

**주요 기능**:
- 아이디 중복 확인 (실시간 AJAX)
- 닉네임 중복 확인 (실시간 AJAX)
- 선호팀 선택 (10개 KBO 팀 드롭다운)
- 이메일 도메인 선택 (gmail.com, naver.com, 직접입력)

**입력 폼 구조**:
```
├─ 아이디 (8~12자, 영문+숫자 조합)
│   └─ 중복확인 버튼 (노란색)
├─ 비밀번호 (8~16자, 영문 대소문자+숫자+특수문자)
├─ 비밀번호 확인
├─ 이름
├─ 이메일 (도메인 선택)
├─ 닉네임 (2~8자, 특수문자 불가)
│   └─ 중복확인 버튼 (노란색)
└─ 선호팀 선택 (드롭다운)
```

**유효성 검사 메시지**:
- 성공: text-success (초록색)
- 실패: text-danger (빨간색)
- 실시간 피드백 표시

**버튼**:
- 제출 버튼: "JOIN the LOCK" (노란색, 전체 너비, fw-bold, fs-4)

---

#### 1.3 홈 페이지 (`/user/home`)
**목적**: 사용자 대시보드 (로그인 후 메인 화면)

**레이아웃**: 2단 그리드 (lg:6-5 비율)

**좌측 카드**: KBO 리그 팀 순위
```
├─ 제목: "KBO 리그 팀 순위" (fs-1, fw-bold)
└─ 테이블
    ├─ 헤더: 순위/팀/경기/승/패/무/승률/승차
    └─ 데이터 행
        ├─ 팀 로고 (SVG, 24px x 24px)
        ├─ 팀명
        └─ 통계 데이터
```

**우측 영역**: 2개 카드 세로 배치

**1) My Team 오늘의 경기**
```
├─ 날짜 및 경기장 정보
├─ 대진표 (flexbox 가로 배치)
│   ├─ My Team
│   │   ├─ 팀 로고 (60px circle)
│   │   ├─ 팀명 (fs-3, fw-bold)
│   │   ├─ 상대전적
│   │   └─ 최근 5경기 (승패무 아이콘)
│   ├─ VS (fs-3, text-secondary)
│   └─ 상대 Team (동일 구조)
└─ 경기 없을 시: "오늘은 경기가 없습니다" 메시지
```

**최근 5경기 표시**:
- 승: text-success, "승" 텍스트
- 패: text-danger, "패" 텍스트
- 무: text-secondary, "무" 텍스트
- 폰트 크기 점진적 증가 (오래된 것 작게 → 최근 것 크게)

**2) 주요 선수 카드**
```
├─ 제목: "주요 선수" (fw-bold, fs-4)
└─ 2단 그리드 (타자/투수)
    ├─ 타자
    │   ├─ 레이블: "타자" (text-secondary)
    │   ├─ 선수명 (fs-4, fw-bold)
    │   ├─ WAR (text-primary, fw-bold)
    │   └─ 세부 스탯 (AVG/HR/OPS)
    └─ 투수
        ├─ 레이블: "투수" (text-secondary)
        ├─ 선수명 (fs-4, fw-bold)
        ├─ WAR (text-primary, fw-bold)
        └─ 세부 스탯 (WHIP/W or SV/ERA)
```

**반응형**:
- 데스크톱: 2단 레이아웃
- 모바일: 1단 세로 스택

---

#### 1.4 비밀번호 찾기 (`/user/find-password-view`)
**목적**: 비밀번호 재설정

**2단계 프로세스**:

**Step 1: 사용자 확인**
```
├─ 아이디 입력
├─ 이름 입력
├─ 이메일 입력 (도메인 선택)
└─ "FIND the LOCK" 버튼 (노란색)
```

**Step 2: 비밀번호 재설정** (확인 후 표시)
```
├─ 새 비밀번호 입력
├─ 비밀번호 확인
└─ "재설정 하기" 버튼 (노란색)
```

**전환 애니메이션**:
- Step 1 → Step 2: fadeOut/fadeIn with d-none toggle
- 중앙 정렬 레이아웃 유지

---

#### 1.5 내 보유 카드 (`/user/my-cards`)
**목적**: 사용자가 보유한 선수 카드 관리

**필터 영역**:
```
├─ 등급 필터 (S/A/B/C)
└─ 포지션 필터 (P/1B/2B/3B/SS/OF/C/DH)
```

**카드 그리드**: 반응형 4단 (lg), 3단 (md), 2단 (sm)

**카드 구조** (3D 뒤집기 효과):
```
앞면 (card-front):
├─ 상단 배너 (검은 배경, 투명도 0.75)
│   ├─ 시즌 정보 (좌측)
│   └─ 등급 라벨 (우측, fw-bold)
├─ 선수 이미지 (S등급만 표시, hologram 효과)
├─ 선수 정보
│   ├─ 선수명 (fs-4)
│   └─ 포지션 / 팀 (로고 포함)
└─ 주요 스탯
    ├─ WAR
    ├─ 포지션별 주요 스탯
    │   투수: ERA, WHIP, W/SV/HLD 중 최댓값
    │   타자: AVG, OPS, HR/SB 중 최댓값
    └─ 종합 능력치 (fw-bold, fs-5)

뒷면 (card-back):
├─ 삭제 버튼 (우상단, 🗑️ 이모지)
├─ "능력치 요약" 제목
└─ 세부 능력치
    투수: 컨트롤/구위/지구력
    타자: 파워/컨택/선구/스피드
```

**등급별 배경 텍스처**:
- S등급: hologram.jpg (홀로그램 효과)
- A등급: gold-fiber.jpg (금색 섬유)
- B등급: silver-fiber.jpg (은색 섬유)
- C등급: linen.jpg (리넨 패턴)
- D등급: cardboard.jpg (골판지 패턴)

**카드 인터랙션**:
- 마우스 좌측 절반: 앞면 유지
- 마우스 우측 절반: 뒷면으로 회전 (rotateY 180deg)
- 마우스 떠남: 앞면으로 복귀
- 삭제 버튼 클릭: 확인 후 DELETE 요청

**등장 애니메이션**:
```css
@keyframes fadeIn {
  from: opacity 0, scale 0.8
  to: opacity 1, scale 1
}
```

---

### 2. Game 모드 페이지

#### 2.1 게임 모드 선택 (`/game/home-view`)
**목적**: 3가지 게임 모드 선택

**배경**: 야구장 이미지 (stadium-bg.png) + 어두운 오버레이 (rgba(0,0,0,0.5))

**레이아웃**: 3단 그리드 카드

**카드 공통 스타일**:
```
├─ 크기: min-height 180px
├─ 배경: linear-gradient(135deg, #f8f9fa, #dee2e6)
├─ Border Radius: 1rem
├─ Shadow: 0 4px 10px rgba(0,0,0,0.1)
└─ Hover 효과
    ├─ transform: translateY(-6px) scale(1.03)
    └─ shadow: 0 8px 20px rgba(0,0,0,0.2)
```

**3가지 모드 카드**:

**1) Real Match Mode**
```
├─ 이모지: 🏟️ (fs-2.5rem)
├─ 제목: "Real Match Mode" (fs-1.1rem, fw-600)
└─ 설명: "실제 KBO 스케줄과 라인업으로 진행하는 리얼 시뮬레이션"
```

**2) Custom Hitter Mode**
```
├─ 이모지: ⚔️
├─ 제목: "Custom Hitter Mode"
└─ 설명: "나만의 타자를 키우는 실시간 RPG형 야구 게임"
```

**3) Classic Sim Mode**
```
├─ 이모지: 🎲
├─ 제목: "Classic Sim Mode"
└─ 설명: "라인업 구성 후 자동 진행되는 클래식 시뮬레이션"
```

**반응형**:
- Desktop: 3단 가로 배치
- Tablet: 2단
- Mobile: 1단 세로 스택

---

#### 2.2 Classic Sim Mode (`/game/classic-sim-mode`)
**목적**: 카드 기반 시뮬레이션 모드 허브

**페이지 헤더**:
```
├─ 제목: "🎮 Classic Sim Mode" (2.5rem, gradient text)
│   Gradient: linear-gradient(135deg, #667eea 0%, #764ba2 100%)
└─ 부제: "카드 컬렉션으로 나만의 최강팀을 구성하고..."
```

**4가지 메뉴 카드** (grid 반응형):

**1) 카드 보관함** (Purple Gradient)
```
├─ 아이콘: 🗂️ (4rem)
├─ 제목: "카드 보관함"
├─ 설명: "보유한 선수 카드들을 확인하고 관리"
└─ 상태: "이용 가능" (bg-success badge)
```

**2) 라인업 구성** (Pink Gradient)
```
├─ 아이콘: 📋
├─ 제목: "라인업 구성"
├─ 설명: "보유한 카드로 최적의 라인업을 구성"
└─ 상태: "이용 가능"
```

**3) 카드 뽑기** (Peach Gradient)
```
├─ 아이콘: 🎰
├─ 제목: "카드 뽑기"
├─ 설명: "새로운 선수 카드를 획득하세요"
└─ 상태: "준비중" (bg-warning badge)
```

**4) 게임 시작** (Mint Gradient)
```
├─ 아이콘: ⚾
├─ 제목: "게임 시작"
├─ 설명: "구성한 라인업으로 시뮬레이션 경기 진행"
└─ 상태: "이용 가능"
```

**빠른 시작 섹션** (하단):
```
├─ 배경: Purple Gradient (전체)
├─ 제목: "⚡ 빠른 시작"
├─ 설명: "기본 라인업으로 바로 게임 시작"
└─ 난이도 버튼 3개 (가로 배치)
    ├─ 😊 쉬움 (Easy)
    ├─ 😐 보통 (Normal)
    └─ 😤 어려움 (Hard)
```

**카드 Hover 애니메이션**:
```css
.menu-card::before {
  /* 빛나는 효과 (왼쪽 → 오른쪽) */
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.2), transparent)
}
```

---

#### 2.3 Real Match Mode (`/game/real-match-mode`)
**목적**: 실제 KBO 경기 시뮬레이션

**페이지 헤더**:
```
├─ 제목: "⚾ Real Match Mode" (Red Gradient)
└─ 부제: "실제 KBO 경기를 기반으로 시뮬레이션 진행"
```

**날짜 선택 섹션**: (어제 경기 기준)

**경기 카드 그리드**: 반응형 3단 (lg), 2단 (md), 1단 (sm)

**경기 카드 구조**:
```
├─ 상태별 스타일
│   ├─ 선택됨: border-color #e74c3c, bg #fff5f5
│   └─ Hover: translateY(-5px), shadow 증가
├─ 대진 정보
│   ├─ 홈팀
│   │   ├─ 로고 (60px circle)
│   │   ├─ 팀명
│   │   └─ 점수 (승리 팀 fw-bold, text-danger)
│   ├─ VS
│   └─ 원정팀 (동일 구조)
└─ 경기 정보
    ├─ 경기 시간 (HH:mm)
    ├─ 경기장
    └─ 상태 배지
        ├─ 경기 종료 (bg-success)
        ├─ 경기 취소 (bg-danger)
        └─ 예정 (bg-warning)
```

**하단 버튼**:
```
"🎮 시뮬레이션 시작" (Red Gradient, 큰 버튼)
- 경기 선택 전: disabled
- 경기 선택 후: active
```

**경기 없을 시**:
```
├─ 이모지: 😔
├─ 메시지: "어제 경기가 없습니다"
└─ 보조 텍스트: "월요일은 일반적으로 경기가 없는 날입니다"
```

---

#### 2.4 Custom Hitter Mode (`/game/custom-hitter-mode`)
**목적**: 나만의 타자 생성 및 육성

**페이지 레이아웃**: 2단 (5:7 비율)

**좌측 - 미리보기 섹션** (Purple Gradient 배경):
```
├─ 플레이어 아바타 (150px circle)
│   ├─ 기본: 👤 플레이스홀더
│   └─ 생성 후: DiceBear API 아바타
├─ 선수명 (1.5rem, fw-bold, white)
├─ 기본 정보 (2x2 그리드)
│   ├─ 나이 / 타석
│   └─ 신장 / 몸무게
├─ 포지션
└─ 능력치 (2x2 그리드)
    ├─ 파워 / 컨택
    ├─ 스피드 / 수비
    └─ 총 능력치: 0 / 120
```

**능력치 재분배 버튼**:
```
├─ 버튼: "🎲 능력치 재분배" (Orange Gradient)
├─ 횟수 표시: "남은 재분배 횟수: X회"
└─ 최대 3회 제한
```

**우측 - 입력 폼 섹션**:
```
기본 정보:
├─ 선수 이름 (maxlength 10)
├─ 나이 (18~45)
└─ 포지션 (select: C/1B/2B/3B/SS/LF/CF/RF/DH)

외모:
├─ 성별 (👨 남성 / 👩 여성)
├─ 피부색 (🟡 밝음 / 🟠 보통 / 🟤 어두움)
└─ 머리 스타일
    ├─ ✂️ 스포츠컷
    ├─ 👦 짧은머리
    ├─ 🧑 중간머리
    ├─ 🌀 곱슬머리
    └─ 🪥 삭발

신체 정보:
├─ 신장 (160~200cm)
├─ 몸무게 (50~150kg)
└─ 타석 (우타/좌타)

버튼:
├─ "🎨 정교한 아바타 생성" (Purple Gradient)
│   └─ 로딩: spinner + "아바타 생성 중..."
└─ "⚾ 선수 생성" (Green Gradient)
    └─ 처음엔 disabled, 아바타 생성 후 활성화
```

**옵션 버튼 스타일**:
```css
.option-btn {
  padding: 0.75rem;
  border: 2px solid #e1e5e9;
  border-radius: 10px;
  transition: all 0.3s;
}
.option-btn.active {
  border-color: #667eea;
  background: #667eea;
  color: white;
}
```

**능력치 안내 박스**:
```
└─ 배경: #fff3cd (연한 노란색)
└─ 내용: "신인 선수로 시작하므로 모든 능력치는 10~50 범위..."
```

**아바타 생성 Fallback 순서**:
1. DiceBear 7.x avataaars
2. DiceBear 6.x avataaars
3. DiceBear 5.x
4. Robohash
5. UI-Avatars
6. Canvas 기반 아바타 (최종)

---

#### 2.5 게임 플레이 화면 (`/game/play`)
**목적**: 실시간 야구 시뮬레이션 진행

**배경**: 야구장 이미지 (ground.jpg) + 어두운 오버레이

**상단 - 스코어보드**:
```
├─ 테이블 (투명 검은 배경 0.3)
├─ 헤더: 팀 / 1~9이닝 / 합계
├─ 유저팀 행 (🧑 아이콘)
└─ 봇팀 행 (🤖 아이콘)
```

**중앙 - 야구장 영역**:
```
├─ 베이스 포지션 (absolute positioning)
│   ├─ 홈: home.png (35px)
│   ├─ 1루: base.png (35px)
│   ├─ 2루: base.png (35px)
│   └─ 3루: base.png (35px)
└─ 주자 아이콘 (3rem, absolute)
    유저: 🧍‍♂️ 🏃‍♂️ 🏃
    봇: 🤖 👾 🛸
```

**주자 이동 애니메이션**:
```css
.runner-icon {
  transition: top 0.2s ease, left 0.2s ease;
  transform: translate(-50%, -50%);
  z-index: 10;
}
```

**중계 방송 영역**:
```
├─ 크기: 1200px x 120px
├─ 배경: rgba(0,0,0,0.6)
├─ Border: 2px solid #ffffff44
├─ 내용 박스
│   ├─ 제목: "📺 중계 방송" (white)
│   └─ 스크롤 영역 (80px height)
│       ├─ overflow-y: auto
│       ├─ scrollbar: hidden
│       └─ 실시간 텍스트 업데이트
```

**라인업 박스** (좌우):
```
├─ 크기: 1000px x 600px
└─ 미니 카드 (card-mini)
    ├─ 크기: 90px 너비
    ├─ 배경: #f8f9fa
    ├─ Border: 1px solid #ccc
    └─ 내용: 선수명, 포지션, 타율
```

**게임 시작 버튼**:
```
"🏁 경기 시작" (노란색, fw-bold, px-4 py-2)
```

**이벤트 선택 모달**:
```
├─ 제목: "⚾ 선택 이벤트"
├─ 질문 텍스트
└─ 선택 버튼들 (d-grid gap-2)
```

---

### 3. Ranking 페이지

#### 3.1 팀 랭킹 (`/ranking/teamranking-view`)
**목적**: KBO 팀 및 선수 통계 순위

**상단 - 시즌 선택**:
```
├─ 버튼 그룹
│   ├─ "팀 랭킹" (노란색 active)
│   └─ "개인 랭킹" (노란색)
└─ 시즌 선택 (select: 2020~2025)
```

**레이아웃**: 4:8 비율

**좌측 - 팀 순위 카드**:
```
├─ 제목: "KBO 리그 팀 순위" (fw-bold)
└─ 테이블 (스크롤)
    ├─ 헤더: 순위/팀/경기/승/패/무/승률/승차
    └─ 데이터 행
        ├─ 순위 (1~3위 노란색 강조)
        ├─ 팀 로고 + 팀명
        └─ 통계
```

**우측 - 3개 스탯 카드**:

**1) 팀 타자 스탯 Top** (6단 그리드)
```
├─ 제목: "팀 타자 스탯 Top"
└─ 리스트
    ├─ 항목 구조
    │   ├─ 카테고리명 (좌측)
    │   └─ 팀 로고 + 팀명 + 값 (우측)
    └─ 카테고리
        AVG, OPS, HR, SB 등
```

**2) 팀 투수 스탯 Top** (6단 그리드)
```
└─ 카테고리: ERA, WHIP, SO, W 등
```

**3) 팀 WAA 스탯 Top** (전체 너비)
```
└─ 카테고리: 타격, 주루, 수비, 선발, 불펜
```

**하단 - 스탯 테이블**:
```
├─ 배경: 흰색, 노란색 border
├─ Shadow: 0 0 15px rgba(255, 215, 0, 0.3)
├─ 헤더
│   ├─ 배경: #ffd700 (골드)
│   └─ 정렬 링크 (▲▼ 화살표)
└─ 행
    ├─ Hover: background #fff8dc
    └─ 1~3위: 순위 숫자 노란색 강조
```

**AJAX 정렬**:
- 헤더 클릭 시 ASC/DESC 토글
- 정렬 방향 화살표 표시
- 테이블만 부분 갱신

**숫자 포맷팅**:
```
소수점 3자리: AVG, OPS (.000)
소수점 2자리: ERA, WHIP, WAR (0.00)
소수점 1자리: IP (0.0)
정수: W, L, SV, HR, SB
```

---

### 4. Daily Review 페이지

#### 4.1 캘린더 뷰 (`/review/calendar-view`)
**목적**: 월별 경기 일정 및 리뷰 관리

**상단 - 월 선택**:
```
├─ 좌측 화살표 버튼
├─ 제목: "2025년 4월" (클릭 시 날짜 선택)
│   └─ Flatpickr Month Picker
└─ 우측 화살표 버튼
```

**캘린더 테이블**:
```
├─ 열: 월~일 + 요약 (8열)
├─ 헤더: 팀 컬러 border
├─ 셀 (custom-cell)
│   ├─ 높이: 140px (desktop), 80px (mobile)
│   ├─ 날짜 (fw-bold)
│   │   └─ 현재 달 아닌 경우: text-muted
│   ├─ 경기 정보 (여러 개 가능)
│   │   ├─ 팀명 (축약형)
│   │   │   예: "두산", "KT", "삼성"
│   │   ├─ 점수 (승리 팀 fw-bold)
│   │   └─ 상태
│   │       ├─ 점수 있음: 리뷰 버튼
│   │       ├─ 점수 없음 (과거): "취소된 경기"
│   │       └─ 점수 없음 (미래): "예정된 경기"
│   └─ 리뷰 버튼
│       ├─ 리뷰 있음: "리뷰 보기" (팀 컬러)
│       └─ 리뷰 없음: "리뷰 쓰기" (팀 컬러)
└─ 요약 열 (8번째 열)
    ├─ 요약 있음: "요약 보기" 버튼
    └─ 요약 없음: "리뷰를 2개 이상 남겨주세요"
```

**팀별 배경색 적용**:
```css
body.team-bg-1 { background-color: #ed1c24; } /* KIA */
body.team-bg-2 { background-color: #042071; } /* 두산 */
...
```

**반응형**:
```
Desktop (>768px): 테이블 정상 표시
Tablet/Mobile: 가로 스크롤 (min-width: 600px~800px)
```

---

#### 4.2 리뷰 작성 (`/review/write-view`)
**목적**: 경기 리뷰 및 BEST/WORST 선수 선택

**상단 - 경기 정보 박스**:
```
├─ Border: 팀 컬러 (2px solid)
├─ 대진표
│   ├─ 원정팀 (좌측)
│   │   ├─ 로고 (24px)
│   │   ├─ 팀명 (승리 팀 fw-bold, fs-2)
│   │   └─ 점수 (승리 팀 fw-bold)
│   ├─ VS (fs-3, text-secondary)
│   └─ 홈팀 (우측, 동일 구조)
└─ 이닝별 점수 테이블
    ├─ 헤더 배경: #fff3cd
    ├─ 팀명 셀: 팀 컬러 배경, 흰색 텍스트
    ├─ 열: 팀 / 1~9 / R / H / E
    └─ 하단 정보
        ├─ 승리투수
        ├─ 홀드 (여러 명 가능)
        ├─ 세이브
        └─ 패전투수
```

**결정적 장면 Best 5**:
```
├─ 카드 (border-warning, shadow)
├─ 헤더
│   ├─ 아이콘: ⚡ (text-warning, fs-4)
│   └─ 제목: "결정적 장면 Best 5"
└─ 테이블
    ├─ 헤더: 순위/이닝/투수/타자/이전상황/결과/이후상황
    └─ 데이터 행
        ├─ 순위: fw-bold
        ├─ 이닝: fw-bold, text-warning-emphasis
        └─ 결과: 중앙 정렬
```

**리뷰 작성 폼**:
```
├─ 경기 리뷰 (textarea, 100자 제한)
├─ BEST 선수 선택 (multiple select)
├─ WORST 선수 선택 (multiple select)
└─ 제출 버튼
    ├─ 저장: "저장하기" (노란색)
    └─ 삭제: "삭제하기" (빨간색)
```

**Select2 스타일링**:
- 다중 선택 가능
- 검색 기능
- 선택된 항목 badge 표시

---

#### 4.3 주간 요약 (`/review/summary-view`)
**목적**: 한 주간 리뷰 통합 요약

**헤더**:
```
├─ 기간 표시: "YYYY.MM.DD ~ YYYY.MM.DD"
└─ "이번 주 한눈에 보기" (fs-1, fw-bold)
```

**통계 카드 그리드** (3단):
```
├─ 전체 경기 수
├─ 승리 (text-success)
├─ 패배 (text-danger)
├─ 무승부 (text-secondary)
├─ 승률
└─ 득점/실점
```

**일별 리뷰 섹션**:
```
└─ 각 날짜별 아코디언
    ├─ 헤더
    │   ├─ 날짜 (M월 d일)
    │   ├─ 대진 (원정 vs 홈)
    │   └─ 점수
    └─ 본문
        ├─ 리뷰 내용
        ├─ BEST 선수 (badge)
        └─ WORST 선수 (badge)
```

**주간 하이라이트**:
```
├─ BEST 선수 Top 3 (이모지 🥇🥈🥉)
├─ WORST 선수 Top 3
└─ 키워드 클라우드 (선택적)
```

---

### 5. Schedule/Results 페이지

#### 5.1 월별 일정 (`/schedule/result-view`)
**목적**: 월별 경기 일정 및 결과 조회

**상단 - 월 선택**:
```
├─ 이전 버튼 (노란색)
├─ 월 제목 (클릭 시 Flatpickr)
│   └─ 하단 노란색 border (3px)
└─ 다음 버튼 (노란색)
```

**월 제목 Hover 효과**:
```css
.month-title {
  border-bottom: 3px solid #facc15;
  border-right: 3px solid #facc15;
}
.month-title:hover {
  background-color: #fef08a;
  box-shadow: 0 2px 6px rgba(0,0,0,0.1);
}
```

**날짜별 카드**:
```
└─ 각 날짜별 반복
    ├─ 카드 헤더 (노란색 배경)
    │   └─ "M월 d일 (요일)"
    └─ 카드 바디
        └─ 경기 목록 (클릭 시 상세 이동)
            ├─ 경기장 (text-muted, 중앙)
            ├─ 대진표 (5-2-5 그리드)
            │   ├─ 원정팀 (우측 정렬)
            │   │   ├─ 팀명 + 점수
            │   │   │   └─ 승리 팀: fw-bold
            │   │   └─ 로고 (28px)
            │   ├─ VS (중앙, fs-4)
            │   └─ 홈팀 (좌측 정렬)
            └─ Border-bottom (구분선)
```

**경기 카드 Hover**:
```css
.card-body .d-flex:hover {
  background-color: #f8f9fa;
  cursor: pointer;
}
```

**반응형**:
- Desktop: 전체 표시
- Mobile: 폰트 크기 축소 (fs-4 → 작게)

---

#### 5.2 경기 상세 (`/schedule/detail-view`)
**목적**: 개별 경기 상세 정보 및 통계

**레이아웃**: 중앙 정렬 (max-width: 1200px)

**상단 - 경기 요약**:
```
├─ 날짜 및 경기장
├─ 대진표 (3단: 원정-VS-홈)
│   ├─ 원정팀
│   │   ├─ 로고 (60px)
│   │   ├─ 팀명
│   │   └─ 최종 점수
│   └─ 홈팀 (동일)
└─ 승리 팀 강조 (fw-bold, 큰 폰트)
```

**이닝별 점수 테이블**:
```
├─ 헤더: 팀 / 1~9 / R / H / E / B
├─ 원정팀 행
│   └─ 배경: 팀 컬러 (팀명 셀)
└─ 홈팀 행
```

**타자 기록 테이블**:
```
├─ 탭 (원정팀 / 홈팀)
└─ 테이블
    ├─ 열: 타순/이름/포지션/타수/안타/타점/득점
    └─ 정렬 가능
```

**투수 기록 테이블**:
```
└─ 열: 이름/이닝/피안타/실점/자책/삼진/볼넷/ERA
```

**주요 플레이 타임라인**:
```
└─ 각 이닝별
    ├─ 이닝 헤더 (fw-bold)
    └─ 플레이 리스트
        ├─ 홈런 (⚾ 아이콘)
        ├─ 도루 (🏃 아이콘)
        └─ 득점 (🔥 아이콘)
```

---

### 6. Custom Player 페이지

#### 6.1 선수 홈 (`/customplayer/home`)
**목적**: 커스텀 선수 목록 및 관리

**모드 선택 버튼**:
```
├─ ⚡ 간편 모드 (HITTER)
├─ ⭐ RPG 모드
└─ 📋 전체 보기
```

**선수 카드 그리드**: 반응형 3단 (lg), 2단 (md), 1단 (sm)

**선수 카드 구조**:
```
├─ 헤더
│   ├─ 모드 배지
│   │   ├─ RPG: bg-warning (노란색)
│   │   └─ 간편: bg-primary (파란색)
│   └─ 레벨 (Lv. X)
├─ 본문
│   ├─ 선수명 (card-title, fs-5)
│   ├─ 포지션
│   ├─ 능력치 진행바 (4개)
│   │   ├─ 파워 (bg-danger)
│   │   ├─ 정확도 (bg-primary)
│   │   ├─ 스피드 (bg-success)
│   │   └─ 수비 (bg-warning)
│   ├─ 경험치 바 (RPG 모드만)
│   └─ 액션 버튼
│       ├─ 수정 (btn-outline-primary)
│       └─ 삭제 (btn-outline-danger)
└─ 푸터
    └─ 생성일 (small, text-muted)
```

**빈 상태**:
```
├─ 아이콘: 📭 (display-1)
├─ 메시지: "아직 생성된 선수가 없습니다"
└─ 버튼: "첫 선수 만들기" (primary)
```

**새 선수 생성 버튼** (하단 중앙):
```
"➕ 새 선수 만들기" (btn-primary, btn-lg)
```

---

#### 6.2 선수 생성 (`/customplayer/create`)
**목적**: 새로운 커스텀 선수 생성
*Custom Hitter Mode와 동일한 UI 구조 사용*

---

#### 6.3 선수 편집 (`/customplayer/edit`)
**목적**: 기존 선수 정보 수정

**구조**: 생성 페이지와 유사, 기존 데이터 pre-fill

**추가 기능**:
- 삭제 버튼 (btn-danger)
- 능력치 재분배 제한 확인
- 아바타 변경 불가 (잠금)

---

#### 6.4 팀 빌더 (`/customplayer/teambuilder`)
**목적**: 커스텀 선수로 팀 구성

**드래그 앤 드롭 UI**:
```
├─ 선수 풀 (좌측)
│   └─ 선수 카드 (draggable)
├─ 야구장 포지션 (우측)
│   ├─ C (포수)
│   ├─ 1B, 2B, 3B, SS
│   ├─ LF, CF, RF
│   └─ DH
└─ 타순 설정 (1~9번)
```

**포지션 슬롯**:
```css
.position-slot {
  width: 100px;
  height: 100px;
  border: 2px dashed #ccc;
  border-radius: 50%;
}
.position-slot.filled {
  border: 2px solid #28a745;
  background: #d4edda;
}
```

**저장 버튼**:
```
"💾 라인업 저장" (Green Gradient, 전체 너비)
```

---

#### 6.5 훈련 (`/customplayer/training`)
**목적**: 선수 능력치 향상

**훈련 카드 그리드** (2x2):
```
├─ 파워 훈련 (Red)
│   ├─ 아이콘: 💪
│   ├─ 제목: "파워 훈련"
│   ├─ 비용: "100 코인"
│   └─ 효과: "+5 파워"
├─ 정확도 훈련 (Blue)
│   └─ 아이콘: 🎯
├─ 스피드 훈련 (Green)
│   └─ 아이콘: ⚡
└─ 수비 훈련 (Yellow)
    └─ 아이콘: 🛡️
```

**훈련 버튼**:
```
"시작하기" (해당 색상 Gradient)
- 코인 부족 시: disabled
- 훈련 중: 진행바 표시
```

**현재 스탯 진행바** (상단):
```
└─ 각 능력치별 진행바 (0~100)
```

---

## 🎭 공통 UI 패턴

### 버튼 스타일

**Primary 버튼** (노란색 강조):
```css
background-color: #FACC15;
color: black;
border: 1px solid black;
font-weight: bold;
padding: 0.75rem 1.5rem;
border-radius: 8px;

hover:
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(250, 204, 21, 0.4);
```

**Gradient 버튼** (모드별):
```css
Purple: linear-gradient(135deg, #667eea, #764ba2)
Red: linear-gradient(135deg, #e74c3c, #c0392b)
Green: linear-gradient(135deg, #28a745, #20c997)
Orange: linear-gradient(135deg, #ffc107, #fd7e14)
```

**Outline 버튼**:
```css
background: transparent;
border: 2px solid (color);
color: (color);

hover:
  background: (color);
  color: white;
```

---

### 카드 컴포넌트

**기본 카드**:
```css
.card {
  background: white;
  border: none;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
  transition: transform 0.3s, box-shadow 0.3s;
}

.card:hover {
  transform: scale(1.01);
  box-shadow: 0 8px 20px rgba(0,0,0,0.1);
}
```

**강조 카드** (노란색 border):
```css
.border-point {
  border: 2px solid #ffd700;
}
```

**그라디언트 카드** (헤더):
```css
.card-header {
  background: linear-gradient(135deg, ...);
  color: white;
  padding: 1rem 1.5rem;
  font-size: 1.2rem;
  font-weight: bold;
}
```

---

### 테이블 스타일

**기본 테이블**:
```css
.table {
  font-size: 1rem;
}

.table th {
  background-color: #f8f9fa;
  font-weight: bold;
  text-align: center;
  padding: 0.75rem;
}

.table td {
  text-align: center;
  padding: 0.5rem;
  vertical-align: middle;
}
```

**Hover 효과**:
```css
.table tbody tr:hover {
  background-color: #f8f9fa;
  transition: background-color 0.2s;
}
```

**반응형 테이블**:
```html
<div class="table-responsive">
  <table class="table" style="min-width: 1000px;">
    ...
  </table>
</div>
```

---

### 폼 요소

**Input Fields**:
```css
.form-control {
  padding: 0.75rem;
  border: 2px solid #e1e5e9;
  border-radius: 10px;
  font-size: 1rem;
  transition: all 0.3s;
}

.form-control:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}
```

**Select Dropdown**:
```css
.form-select {
  padding: 0.75rem;
  border: 2px solid #e1e5e9;
  border-radius: 10px;
  cursor: pointer;
}
```

**Textarea**:
```css
textarea.form-control {
  resize: vertical;
  min-height: 100px;
}
```

---

### 모달 (Modal)

**구조**:
```html
<div class="modal fade">
  <div class="modal-dialog modal-dialog-centered">
    <div class="modal-content">
      <div class="modal-header">
        <h5 class="modal-title">제목</h5>
        <button class="btn-close"></button>
      </div>
      <div class="modal-body">
        내용
      </div>
      <div class="modal-footer">
        버튼들
      </div>
    </div>
  </div>
</div>
```

**스타일**:
```css
.modal-content {
  border-radius: 15px;
  border: none;
  box-shadow: 0 10px 40px rgba(0,0,0,0.3);
}

.modal-header {
  border-bottom: 1px solid #eee;
  padding: 1.5rem;
}

.modal-body {
  padding: 2rem;
}
```

---

### 배지 (Badge)

**상태 배지**:
```css
.badge {
  padding: 0.5rem 1rem;
  border-radius: 20px;
  font-size: 0.85rem;
  font-weight: bold;
}

.status-ready { /* 이용 가능 */
  background: #d4edda;
  color: #155724;
}

.status-coming-soon { /* 준비중 */
  background: #fff3cd;
  color: #856404;
}

.completed { /* 완료 */
  background: #d4edda;
  color: #155724;
}

.cancelled { /* 취소 */
  background: #f8d7da;
  color: #721c24;
}
```

---

### 진행바 (Progress Bar)

**능력치 진행바**:
```html
<div class="progress" style="height: 20px;">
  <div class="progress-bar bg-danger" style="width: 75%">
    75
  </div>
</div>
```

**색상별**:
- Power: bg-danger (빨강)
- Contact: bg-primary (파랑)
- Speed: bg-success (초록)
- Defense: bg-warning (노란색)
- Experience: bg-info (청록)

---

### 아이콘 및 이모지

**공통 이모지 사용**:
```
⚾ - 야구, 경기
🏟️ - 야구장, Real Match
⚔️ - 대전, Custom Hitter
🎲 - 시뮬레이션, Classic
📺 - 중계, 방송
💪 - 파워, 훈련
🎯 - 정확도
⚡ - 스피드, 빠른 시작
🛡️ - 수비
🏆 - 우승, 순위
⭐ - 최고, BEST
📊 - 통계, 랭킹
📅 - 일정, 캘린더
✍️ - 리뷰 작성
🗂️ - 카드 보관함
🎰 - 카드 뽑기
```

**Bootstrap Icons**:
```
bi-person-badge - 선수
bi-lightning-charge - 결정적 장면
bi-pencil - 수정
bi-trash - 삭제
bi-plus-circle - 추가
bi-inbox - 빈 상태
```

---

## 🎬 애니메이션 및 전환 효과

### GSAP 기반 게임 애니메이션

**주자 이동**:
```javascript
gsap.to(runner, {
  top: targetY,
  left: targetX,
  duration: 0.5,
  ease: "power2.out"
});
```

**타격 효과**:
```javascript
// 홈런
gsap.timeline()
  .to(ball, { scale: 1.5, duration: 0.2 })
  .to(ball, { y: -300, opacity: 0, duration: 1, ease: "power1.in" });

// 안타
gsap.to(ball, {
  x: 150,
  y: -50,
  duration: 0.8,
  ease: "power2.out"
});
```

**점수 추가 애니메이션**:
```javascript
gsap.from(scoreCell, {
  scale: 1.5,
  color: "#ffd700",
  duration: 0.5,
  ease: "back.out"
});
```

---

### CSS 트랜지션

**Hover 효과**:
```css
/* 카드 Hover */
.card {
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}
.card:hover {
  transform: translateY(-5px);
}

/* 버튼 Hover */
.btn {
  transition: all 0.3s ease;
}
.btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 5px 15px rgba(0,0,0,0.2);
}
```

**페이드 인**:
```css
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.fade-in {
  animation: fadeIn 0.5s ease-in-out;
}
```

**카드 뒤집기** (3D):
```css
.card-inner {
  transform-style: preserve-3d;
  transition: transform 0.8s;
}

.card-flip:hover .card-inner {
  transform: rotateY(180deg);
}

.card-back {
  transform: rotateY(180deg);
  backface-visibility: hidden;
}
```

**슬라이드 인**:
```css
@keyframes slideInLeft {
  from { transform: translateX(-100%); }
  to { transform: translateX(0); }
}
```

---

### 로딩 애니메이션

**스피너**:
```html
<div class="spinner-border text-primary" role="status">
  <span class="visually-hidden">로딩 중...</span>
</div>
```

**커스텀 스피너**:
```css
.spinner {
  width: 20px;
  height: 20px;
  border: 3px solid #f3f3f3;
  border-top: 3px solid #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
```

**프로그레스 바 애니메이션**:
```css
.progress-bar {
  transition: width 0.6s ease;
}
```

---

## 📱 모바일 최적화 및 반응형 디자인

### Breakpoints
```
XS: 0~480px (모바일)
SM: 480px~768px (큰 모바일/작은 태블릿)
MD: 768px~1024px (태블릿)
LG: 1024px~1200px (작은 데스크톱)
XL: 1200px+ (데스크톱)
```

### 반응형 규칙

**컨테이너 패딩**:
```css
/* Desktop (1201px+) */
.container-fluid {
  padding-left: 3rem;
  padding-right: 3rem;
}

/* Tablet/Mobile (≤1200px) */
.container-fluid {
  padding-left: 10px !important;
  padding-right: 10px !important;
}
```

**폰트 크기**:
```css
/* Desktop */
body { font-size: 1.1rem; }
h1 { font-size: 2.5rem; }

/* Tablet (≤768px) */
body { font-size: 1rem; }
h1 { font-size: 2rem; }

/* Mobile (≤480px) */
body { font-size: 0.9rem; }
h1 { font-size: 1.5rem; }
```

**로고 크기**:
```css
/* Desktop */
.full-logo-default { font-size: 100px; }

/* Tablet */
.full-logo-default { font-size: 60px; }

/* Mobile */
.full-logo-default { font-size: 45px; }
```

**그리드 변경**:
```html
<!-- Desktop: 3단 -->
<div class="col-lg-4 col-md-6 col-12">

<!-- Tablet: 2단 -->
<!-- Mobile: 1단 (전체 너비) -->
```

**헤더 레이아웃**:
```css
/* Desktop: 가로 배치 */
header {
  flex-direction: row;
}

/* Mobile: 세로 배치 */
@media (max-width: 768px) {
  header {
    flex-direction: column !important;
    align-items: center !important;
  }
}
```

**테이블 스크롤**:
```css
@media (max-width: 768px) {
  .table-responsive {
    overflow-x: auto;
  }

  .table {
    min-width: 1000px; /* 가로 스크롤 강제 */
  }
}
```

**버튼 크기**:
```css
/* Desktop */
.btn { padding: 8px 16px; font-size: 1rem; }

/* Mobile */
@media (max-width: 480px) {
  .btn { padding: 6px 12px; font-size: 0.8rem; }
}
```

---

## 🎯 Common 요소

### Header (헤더)

**구조**:
```html
<header class="d-flex align-items-end px-3 px-md-5">
  <!-- 좌측: 로고 -->
  <div class="flex-grow-1">
    <a href="/user/home">
      <div class="full-logo-default">BB LOCK</div>
    </a>
  </div>

  <!-- 중앙: 네비게이션 -->
  <nav class="flex-grow-2 d-flex gap-2 gap-md-4">
    <a href="/schedule/result-view" class="nav-outline">일정/결과</a>
    <a href="/ranking/teamranking-view" class="nav-outline">랭킹</a>
    <a href="/review/calendar-view" class="nav-outline">데일리 리뷰</a>
    <a href="/game/home-view" class="nav-outline">시뮬레이션</a>
    <a href="#" class="nav-outline">락커룸</a>
  </nav>

  <!-- 우측: 사용자 정보 -->
  <div class="position-absolute top-0 end-0 mt-2 me-2">
    <span class="fw-bold">닉네임 님</span>
    <a href="/user/logout" class="text-warning">로그아웃</a>
  </div>
</header>
```

**스타일**:
```css
header {
  background-color: #f5f5f5;
  border-bottom: 2px solid #facc15;
}

.nav-outline {
  color: #111;
  font-weight: bold;
  text-shadow: 0 0 2px #facc15;
}

.nav-outline:hover {
  color: #facc15;
}
```

---

### Footer (푸터)

**구조**:
```html
<footer class="d-flex justify-content-center align-items-center">
  <div>Copyright 2025. BaseBall LOCK inc. all rights reserved.</div>
</footer>
```

**스타일**:
```css
footer {
  height: 70px;
  background-color: #eaeaea;
  text-align: center;
  line-height: 70px;
  font-size: 0.95rem;
  color: #666;
}
```

---

### Layout Templates

**1) default.html** (헤더+푸터 포함):
```html
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>BaseBall LOCK</title>

  <!-- Fonts -->
  <link href="https://fonts.googleapis.com/css2?family=Dokdo&display=swap" rel="stylesheet">
  <link href="https://cdn.jsdelivr.net/npm/pretendard@1.3.8/dist/web/static/pretendard.css" rel="stylesheet">

  <!-- Bootstrap 5.1.3 -->
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/css/bootstrap.min.css" rel="stylesheet">

  <!-- Bootstrap Icons -->
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">

  <!-- Flatpickr (날짜 선택) -->
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/flatpickr/dist/themes/confetti.css">

  <!-- Custom CSS -->
  <link href="/static/css/style.css" rel="stylesheet">
</head>
<body>
  <th:block th:replace="~{fragments/header.html}"></th:block>

  <th:block layout:fragment="contents"></th:block>

  <th:block th:replace="~{fragments/footer.html}"></th:block>

  <!-- jQuery 3.7.1 -->
  <script src="https://code.jquery.com/jquery-3.7.1.min.js"></script>

  <!-- Bootstrap JS -->
  <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/js/bootstrap.min.js"></script>

  <th:block layout:fragment="script"></th:block>
</body>
</html>
```

**2) noheader.html** (푸터만):
- 로그인, 회원가입 페이지용
- 헤더 제외, 푸터 포함

**3) noheadefooter.html** (둘 다 제외):
- 게임 플레이 화면용
- 전체 화면 레이아웃

**4) userDefault.html** (커스텀 헤더):
- 커스텀 플레이어 모드용
- 다른 스타일의 헤더 사용

---

## 📝 디자인 가이드라인

### 일관성 원칙

1. **색상 사용**:
   - Primary Action: 항상 노란색 (#FACC15)
   - Destructive Action: 빨간색 (#DC3545)
   - Success: 초록색 (#28A745)
   - 팀 컬러: 정확한 HEX 코드 사용

2. **타이포그래피**:
   - 로고: Dokdo만 사용
   - 본문: Pretendard만 사용
   - 최소 폰트 크기: 0.75rem (모바일)

3. **여백**:
   - 섹션 간: 2rem~3rem
   - 요소 간: 1rem~1.5rem
   - 카드 내부: 1rem~2rem

4. **Border Radius**:
   - 작은 요소: 8px
   - 중간 요소: 12px~15px
   - 큰 요소: 20px

---

### 접근성 고려사항

1. **색상 대비**:
   - 텍스트/배경 대비비 최소 4.5:1 유지
   - 중요 정보는 색상 외 다른 방법으로도 전달

2. **키보드 네비게이션**:
   - 모든 인터랙티브 요소 Tab 접근 가능
   - Focus 상태 명확히 표시

3. **반응형**:
   - 최소 360px 너비 지원
   - Touch target 최소 44px x 44px

4. **의미론적 HTML**:
   - 적절한 heading 계층 (h1 → h2 → h3)
   - Semantic tags 사용 (header, nav, main, footer)

---

### 성능 최적화

1. **이미지**:
   - SVG 사용 (팀 로고, 아이콘)
   - 적절한 크기로 압축
   - Lazy loading 적용

2. **CSS**:
   - 최소화 및 압축
   - 중요 CSS 인라인 처리
   - Unused CSS 제거

3. **JavaScript**:
   - 번들 크기 최소화
   - 지연 로딩 (defer, async)
   - AJAX 요청 최적화

4. **폰트**:
   - Woff2 형식 우선 사용
   - font-display: swap
   - 필요한 글꼴만 로드

---

## 🎨 디자인 체크리스트

### 페이지별 체크리스트

**모든 페이지 공통**:
- [ ] 헤더/푸터 일관성
- [ ] 반응형 레이아웃 (모든 breakpoint)
- [ ] 로딩 상태 표시
- [ ] 에러 처리 UI
- [ ] 빈 상태 (Empty State) 디자인

**폼 페이지**:
- [ ] 입력 필드 검증 (실시간)
- [ ] 에러 메시지 명확성
- [ ] 제출 버튼 상태 (disabled/loading)
- [ ] 성공/실패 피드백

**데이터 테이블**:
- [ ] 정렬 기능 (ASC/DESC)
- [ ] 반응형 스크롤
- [ ] Hover 효과
- [ ] 빈 데이터 처리

**카드 그리드**:
- [ ] 일관된 카드 높이
- [ ] 적절한 간격
- [ ] Hover 애니메이션
- [ ] 반응형 그리드

---

## 🚀 Google Stitch 프롬프트 요약

**프로젝트**: BaseBall LOCK 2.0 - KBO 야구 시뮬레이션 웹 애플리케이션

**핵심 디자인 요소**:
1. 메인 컬러: #FACC15 (노란색) + 10개 KBO 팀 컬러
2. 폰트: Dokdo (로고), Pretendard (본문)
3. 레이아웃: 1200px max-width, Bootstrap 5 기반
4. 애니메이션: GSAP + CSS transitions
5. 반응형: 360px~1200px+ 완전 지원

**주요 페이지**:
- 사용자 관리 (로그인/회원가입/홈/마이카드)
- 게임 모드 3가지 (Real Match/Custom Hitter/Classic Sim)
- 랭킹 시스템 (팀/선수 통계)
- 리뷰 시스템 (캘린더/작성/요약)
- 일정/결과 조회

**특수 기능**:
- 3D 카드 뒤집기 (선수 카드)
- GSAP 기반 야구 시뮬레이션
- 실시간 능력치 진행바
- 드래그 앤 드롭 팀 빌더
- 월간/주간 캘린더 뷰

**디자인 철학**:
- 명확성: 야구 데이터 직관적 표시
- 몰입감: 애니메이션 및 시각 효과
- 접근성: 모든 기기에서 동일한 경험
- 일관성: 전체 UI 패턴 통일

---

이 프롬프트는 Google Stitch에서 BaseBall LOCK 2.0의 전체 UI/UX를 이해하고 디자인할 수 있도록 작성되었습니다. 각 페이지의 목적, 레이아웃, 컴포넌트, 색상, 애니메이션까지 상세히 포함되어 있으며, 실제 템플릿 파일과 CSS를 기반으로 작성되었습니다.
