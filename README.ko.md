# Curio

[English](README.md) · **한국어**

**AI 모델 소식을 매일 골라 전하는 브리핑. 5문항 퀴즈로 읽은 내용을 오래 기억하게 돕습니다.**

Curio는 Anthropic, OpenAI, DeepMind, Meta AI, Mistral, Hugging Face 같은 연구소의 공식 블로그와 NewsAPI에서 기사를 모읍니다. 그중 사용자가 고른 주제만 Claude가 쉬운 말로 요약하고, 사용자가 사는 지역의 받는 시간에 맞춰 이메일로 보냅니다. 다 읽고 나면 퀴즈로 얼마나 기억하는지 확인할 수 있습니다.

![Curio 랜딩 페이지](docs/screenshots/ko/home.png)

## 이렇게 동작해요

1. **주제 고르기**: 최신 모델, 코딩 에이전트, 가격, 벤치마크 등 AI 모델 중심의 주제 18개 중 3개 이상을 고릅니다.
2. **아침 다이제스트 읽기**: 기사는 최대 8개이고, 기사마다 제목, 50~80단어 요약, '왜 중요한가'가 붙습니다. 웹과 이메일에서 모두 볼 수 있습니다.
3. **퀴즈 풀기**: 오늘 다이제스트로 만든 5문항을 풀면 바로 채점하고 해설을 보여 줍니다.

![퀴즈 풀기: 5문항에 답하면 점수와 해설이 나타납니다](docs/screenshots/ko/quiz-flow.gif)

## 화면

| 오늘의 다이제스트 | 주제 선택 |
|---|---|
| ![오늘의 다이제스트가 보이는 아카이브](docs/screenshots/ko/archive.png) | ![온보딩 주제 선택](docs/screenshots/ko/onboarding.png) |
| **다이제스트 스튜디오**: 원할 때 직접 만들고 보내기 | **영어판**: 화면과 AI 콘텐츠 모두 영어 |
| ![다이제스트 스튜디오](docs/screenshots/ko/studio.png) | ![영어판 아카이브](docs/screenshots/archive.png) |
| **설정**: 받는 시간과 시간대 | **뉴스룸(관리자)**: 독자, 다이제스트, 작업, 감사 로그 |
| ![받는 시간과 시간대 설정](docs/screenshots/ko/settings.png) | ![관리자 통계](docs/screenshots/ko/admin-stats.png) |

<sub>스크린샷은 샘플 데이터로 찍었습니다.</sub>

## 주요 기능

- **맞춤 일간 다이제스트**: 고른 주제에서 기사를 고르게 뽑아, 정한 시간에 사용자의 시간대 기준으로 보냅니다.
- **복습 퀴즈**: 다이제스트마다 5문항을 서버에서 채점하고 기록을 남깁니다. 다시 풀면 더 높은 점수를 남깁니다.
- **English · 한국어**: 모든 화면을 두 언어로 제공합니다. 고른 언어에 따라 AI가 쓰는 다이제스트, 퀴즈, 이메일의 언어도 바뀝니다.
- **내 API 키 사용(BYOK)**: 본인의 Claude, Gemini, OpenAI 키로 다이제스트를 만들 수 있습니다. 키는 암호화해 저장합니다.
- **다이제스트 스튜디오**: 오늘 다이제스트를 직접 만들고 이메일을 보내며, 진행 상황을 실시간으로 볼 수 있습니다.
- **뉴스룸 대시보드**: 독자, 다이제스트, 주제 통계, 예약 작업 상태와 수동 실행, 감사 로그를 확인합니다.
- **안전한 로그인**: 이메일과 비밀번호를 입력한 뒤 메일로 받은 6자리 코드로 확인합니다. Google 로그인도 지원합니다.

## 기술 스택

| | |
|---|---|
| **프론트엔드** | Vue 3 · TypeScript · Vite · Pinia · Tailwind CSS v4 · vue-i18n |
| **백엔드** | Java 21 · Spring Boot 3.5 · PostgreSQL 16 · Redis 7 · Flyway |
| **AI·외부 서비스** | Claude(기본) / Gemini / OpenAI · NewsAPI · Resend |

버전과 나머지 스택은 [`docs/CODEBASE.md`](docs/CODEBASE.md#technology-stack)에서 확인할 수 있습니다.

## 빠르게 시작하기

Java 21, Maven 3.9 이상, Node.js 20 이상, Docker가 필요합니다.

```bash
# 1. Postgres + Redis
cp .env.dev.example .env.dev
docker compose --env-file .env.dev -f docker-compose.infra.yml up -d

# 2. 백엔드 → http://localhost:8080
cd backend && cp .env.example .env    # CLAUDE_API_KEY, RESEND_API_KEY, NEWS_API_KEY 입력
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 3. 프론트엔드 → http://localhost:5173
cd frontend && npm install && npm run dev
```

> 백엔드를 실행할 때는 항상 `dev` 프로필을 지정하세요. 프로필 없이 `mvn spring-boot:run`만 실행하면 운영용 `prod` 프로필로 시작하고, 실제 시크릿이 없으면 바로 멈춥니다.

설치 과정 전체와 문제 해결 방법은 [`docs/SETUP.md`](docs/SETUP.md)에, API 키 발급 방법은 [`docs/SETUP_API_KEYS.md`](docs/SETUP_API_KEYS.md)에 있습니다.

## 저장소 구성

```
curio/
├── frontend/   Vue 3 SPA              → frontend/README.md
├── backend/    Spring Boot REST API   → backend/README.md
├── docs/       설치, 아키텍처, 레퍼런스, 배포 문서
└── scripts/    백업/복원, 배포, k6 부하 테스트
```

## 문서

`docs/` 아래 상세 문서는 영어로 작성돼 있습니다.

| 문서 | 이럴 때 읽어요 |
|---|---|
| [SETUP.md](docs/SETUP.md) | 로컬에서 실행하거나 설치 문제를 해결할 때 |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | 시스템 구조, 데이터 흐름, 기능 내부 동작을 이해할 때 |
| [CODEBASE.md](docs/CODEBASE.md) | 엔드포인트, 테이블, 마이그레이션, 컴포넌트, 테스트를 찾을 때 |
| [ENV_VARIABLES.md](docs/ENV_VARIABLES.md) | 환경 변수가 하는 일을 확인할 때 |
| [DEPLOY.md](docs/DEPLOY.md) | Docker Compose와 TLS로 운영 환경에 배포할 때 |

## 기여하기

브랜치·커밋 규칙, PR 전에 돌릴 검사, 코드 규칙은 [`CONTRIBUTING.md`](./CONTRIBUTING.md)에 있습니다. 보안 문제는 [`SECURITY.md`](./SECURITY.md)에 안내된 방법으로 비공개로 제보해 주세요.

## 라이선스

Proprietary
