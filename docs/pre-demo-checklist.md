# QR 시연 전에 직접 설정할 항목

## 이메일 인증 발송 방식 — 확정

지금 개발은 공통 SMTP 방식으로 진행합니다. 나중에 Gmail이나 다른 발송 서비스의 접속 정보를 설정할 수 있습니다. 테스트용 대체 발송은 자동 테스트에서만 사용하고, QR로 접속하는 시연 환경에서는 실제 발송을 연결합니다. 프로젝트 실행만으로 메일이 발송되는 것은 아닙니다.

- 인증 링크 유효시간: 30분
- 같은 이메일의 재발송 간격: 60초
- 확인 후 발급하는 가입 증명: 10분, 일회용
- 일반 이메일 가입용 인증이며 Google 소셜 로그인과 별개입니다.

## 사용자가 준비·입력할 값 — 미완료

| 환경변수/설정 | 입력 내용 | 비고 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `mysql,auth,smtp` | bootstrap은 DB 없는 기동 확인 전용 |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | 시연 DB 접속 정보 | 개발·테스트 DB와 구분 |
| `SMTP_HOST` | 발송 서비스 SMTP 호스트 | Gmail 선택 시 smtp.gmail.com |
| `SMTP_PORT` | STARTTLS 지원 포트 | 기본 587. 현재 설정은 STARTTLS 방식 |
| `SMTP_USERNAME` | 발신 계정의 인증 ID | 서비스별 안내 확인 |
| `SMTP_PASSWORD` | 앱 비밀번호 또는 SMTP 전용 자격증명 | 채팅·Git·공유 XML에 입력하지 않음 |
| `MAIL_FROM` | 발송 서비스가 허용하는 발신 이메일 | 임의 주소 사용 금지 |
| `EMAIL_VERIFICATION_FRONTEND_URL` | HTTPS 인증 확인 화면의 전체 주소 | 예: https://실제주소/verify-email. 쿼리·fragment 없이 지정 |
| `EMAIL_VERIFICATION_IP_MAX_PER_MINUTE` | 같은 IP의 분당 발송 상한 | 기본 30회. 현장 공용 Wi-Fi/NAT·프록시 구성에 맞춰 조정 |

환경변수는 배포 서비스의 비밀값 설정 또는 IntelliJ 실행 구성으로 주입합니다. `.env.example`은 안내용이며 Spring Boot가 `.env`를 자동으로 읽지 않습니다. 비밀값을 보내주실 필요는 없습니다.

### Gmail로 발송할 경우

- [ ] 프로젝트용 발신 계정 준비
- [ ] 2단계 인증 및 앱 비밀번호 사용 가능 여부 확인
- [ ] 일반 계정 비밀번호 대신 SMTP용 자격증명 등록
- [ ] 발송 제한·스팸함·메일 도착 시간 확인

계정 상태에 따라 앱 비밀번호를 사용할 수 없는 경우 다른 발송 서비스를 선택합니다. Gmail을 선택한다고 수신자의 주소가 Gmail로 제한되는 것은 아닙니다.

공식 안내: [Google 앱 비밀번호](https://support.google.com/mail/answer/185833?hl=ko), [Spring Boot SMTP](https://docs.spring.io/spring-boot/reference/io/email.html).

## 프론트와 맞출 항목 — 미완료

- [ ] QR은 배포된 HTTPS 서비스 주소로 연결
- [ ] 위 환경변수에 지정한 인증 확인 화면 구현
- [ ] 링크의 `#token=...`을 읽고 주소창 fragment 정리. 서버 접근 로그로 토큰을 전달하는 query 방식으로 변경하지 않음
- [ ] 확인 동작에서 `POST /api/v1/auth/email-verifications/confirm`에 JSON `{ "token": "..." }` 전달
- [ ] 성공 응답의 `signupVerificationToken`을 메모리에 보관하여 후속 회원가입 API에 전달
- [ ] 400·409·410·429·503 응답에 대한 안내·재발송 경로 구현
- [ ] 프론트·백엔드가 다른 Origin이면 실제 허용 Origin에 맞춘 CORS 설정 확정. `CORS_ALLOWED_ORIGINS`에 정확한 프론트 Origin을 지정하며 와일드카드를 사용하지 않음

## 시연 전 실제 동작 확인 — 미완료

- [ ] 시연 환경에 실제 SMTP 설정 적용. 테스트 대체 발송 코드를 배포에 추가하지 않음
- [ ] 실기기에서 QR 접속 → 인증 메일 수신 → HTTPS 링크 열기 → 인증 확인 → 가입 화면 복귀 확인
- [ ] 같은 계정 재발송 제한·만료·사용한 링크 재접속 확인
- [ ] 이메일 발송 실패 때 성공 안내가 뜨지 않고 재시도 안내가 뜨는지 확인
- [ ] SMTP 연결 정보·링크 토큰·가입 증명이 로그·분석 도구·오류 수집기에 남지 않는지 확인
- [ ] 실제 발신 계정으로 도착 시험 후 시연 가능 상태로 표시

현재 실제 SMTP 발신 계정·배포 주소는 미설정이며 실제 메일 수신은 검증하지 않았습니다. 자동 테스트 통과를 실제 수신 성공으로 간주하지 않습니다. Google 로그인·토큰 발급은 아래 설정 후 실제 연동 검증이 필요합니다. 일반 이메일 최종 회원가입·홈 진입은 후속 기능입니다.

## Google 로그인·토큰 설정 — 직접 설정 필요

| 환경변수/설정 | 입력 내용 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `mysql,auth`, 이메일 발송도 사용하면 `mysql,auth,smtp` |
| `GOOGLE_CLIENT_ID` | Google Cloud의 웹 애플리케이션 Client ID. 프론트와 서버가 같은 값 사용 |
| `JWT_SECRET_BASE64` | 32바이트 이상 무작위 키의 Base64 값. `openssl rand -base64 32`로 생성 후 비밀값 설정에 저장. 채팅·Git에 공유 금지 |
| `CORS_ALLOWED_ORIGINS` | 실제 프론트 Origin. 예: `https://app.example.com` (끝 `/` 없음). 여러 개는 쉼표 구분 |
| `AUTH_COOKIE_SAME_SITE` | 같은 site 배포는 `Lax`, 다른 site는 `None` 검토. Secure는 항상 적용. Origin과 site는 다른 개념 |
| `TERMS_VERSION`, `PRIVACY_VERSION`, `MARKETING_VERSION` | 프론트에 표시한 각 문서의 실제 버전. 테스트용 v1을 무심코 사용하지 않음 |

- [ ] Google Cloud 웹 클라이언트의 승인된 JavaScript 원본에 프론트 주소 등록
- [ ] OAuth 동의 화면·사용자 접근 범위·테스트 사용자 설정을 실제 시연 계정으로 확인
- [ ] 프론트에서 challenge 발급 후 nonce를 GIS 초기화에 전달하고, callback으로 받은 credential을 JSON API에 전달
- [ ] 모든 쿠키 API에 `credentials: include` 적용. ID Token이나 Refresh Token을 localStorage에 저장하지 않음
- [ ] Access Token은 메모리에 보관하고 Bearer 헤더로 전달
- [ ] 프론트에서 재발급을 직렬화하고 여러 탭의 동시 요청도 조정. 사용한 토큰을 재전송하면 세션 전체가 폐기됨
- [ ] QR로 접속한 실제 Chrome·Safari에서 Google 로그인 → 새로고침 후 재발급 → 로그아웃 확인
- [ ] HTTPS와 실제 쿠키 송수신 확인. 다른 site 배포는 브라우저의 제3자 쿠키 정책으로 차단될 수 있어 같은 site 배포를 우선 검토

이 구현은 GIS의 JS callback → 자체 JSON API 방식입니다. Google의 redirect POST를 이 API로 바로 연결하지 않습니다. Client Secret은 사용하지 않습니다. 실제 Google 계정 로그인과 배포 환경의 쿠키 연동은 아직 검증하지 않았습니다.
