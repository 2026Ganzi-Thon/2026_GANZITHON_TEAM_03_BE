# 이메일 인증 API

## 구현 범위

| API | 요청 | 성공 |
| --- | --- | --- |
| POST /api/v1/auth/email-verifications | `{ "email": "user@example.com" }` | 202, `data.message` |
| POST /api/v1/auth/email-verifications/confirm | `{ "token": "메일 링크의 토큰" }` | 200, `data.email`, `data.signupVerificationToken`, `data.expiresIn=600` |

토큰 원문은 메일 링크와 확인 응답의 가입 증명에만 사용합니다. 발송 응답에는 반환하지 않으며 DB에는 SHA-256 해시만 보관합니다. 메일 토큰은 32바이트 SecureRandom으로 생성합니다. 확인은 이메일 인증 토큰만, 후속 가입 증명 소비는 SIGNUP_VERIFICATION 목적만 허용합니다. 토큰/가입 증명은 로그인 Access Token이 아닙니다.

에러는 `code`, `message`, `fieldErrors`입니다. 입력값·원문 토큰·SMTP 오류 상세를 오류 응답에 포함하지 않습니다. 성공 인증 응답은 `Cache-Control: no-store`입니다.

| HTTP | 코드 | 처리 |
| --- | --- | --- |
| 400 | COMMON_INVALID_REQUEST | JSON·이메일 형식·필수값 확인 |
| 400 | AUTH_EMAIL_VERIFICATION_INVALID | 알 수 없는 토큰·다른 용도 |
| 409 | AUTH_EMAIL_VERIFICATION_USED | 이미 사용·재발송으로 무효화된 링크 |
| 410 | AUTH_EMAIL_VERIFICATION_EXPIRED | 만료, 재발송 필요 |
| 429 | AUTH_RATE_LIMITED | 같은 이메일 60초 또는 같은 IP 분당 상한 |
| 503 | AUTH_MAIL_UNAVAILABLE | SMTP 미설정·발송 실패, 기존 링크 유지 |

계정 존재 여부를 조회하지 않고 같은 발송/확인 흐름을 제공합니다. 기존 회원의 이메일도 인증할 수 있지만 계정을 변경하거나 로그인시키지 않습니다. 실제 일반 회원가입에서는 중복 이메일을 별도로 거절해야 합니다.

## 저장과 동시 요청

Flyway V2는 `auth_verifications`, `email_verification_limits`를 추가합니다. 발송은 IP 제한 행 → 이메일 제한 행 순서로 잠그고, 재발송은 같은 이메일의 이전 미사용 메일 토큰을 무효화합니다. 실패 시 이전 링크를 보존합니다. IP 제한은 기본 30회/분으로 조정 가능하며 원문 IP 대신 해시 키만 저장합니다.

확인·후속 가입 증명 소비는 같은 이메일 행으로 직렬화합니다. 동시 확인에서 가입 증명은 하나만 발급됩니다. 새 확인이 성공하면 이전 미사용 가입 증명은 무효화합니다. 소비는 `Propagation.MANDATORY`로 외부 가입 트랜잭션을 요구하므로 가입 실패 시 소비도 롤백됩니다. 아직 실제 가입 API는 없으며 내부 소비 메서드까지만 구현했습니다.

## 구현 선택과 한계

- 시연 규모에 맞춰 제한 시간 있는 동기 SMTP 발송을 사용합니다. SMTP 요청 중 DB 잠금을 유지하므로 대량 발송에는 부적합합니다.
- SMTP 접수 성공과 실제 받은편지함 도착은 다릅니다. SMTP 접수 뒤 DB 커밋이 실패하는 경우 메일은 도착해도 링크가 유효하지 않을 수 있습니다. 재발송으로 복구하며 정확히 한 번 배달을 보장하지 않습니다.
- 스캐너의 GET만으로 인증 토큰을 소비하지 않습니다. 소비는 확인 POST로만 수행합니다. 프론트 확인 화면은 별도 구현이 필요합니다.
- 원격 주소는 `HttpServletRequest.getRemoteAddr()` 기준입니다. 현재 전달 헤더를 임의 신뢰하지 않습니다. 프록시·현장 Wi-Fi 구성에 따른 제한 값과 신뢰 프록시 정책은 배포 전에 확인합니다.
- SMTP adapter는 실제 발송 구현만 존재합니다. 가짜 발송은 `src/test`의 Mockito 대체로만 사용합니다. 설정 누락 시 503이며 가짜 성공을 반환하지 않습니다.
- 만료된 토큰·제한 행 정리 작업은 아직 없습니다. 보관 기간·운영 정리 작업은 별도 합의가 필요합니다.
- 이메일 발송·확인 POST 두 경로만 쿠키 인증 없이 공개하며 CSRF 검사에서 제외합니다. Refresh/로그아웃 같은 쿠키 인증 경로의 CSRF를 일괄 해제하지 않습니다.

## 테스트

`./gradlew build`는 HTTP 계약·SMTP adapter·토큰·비밀번호·기동 테스트입니다. `./gradlew mysqlIntegrationTest`는 전용 로컬 테스트 DB에서 마이그레이션, 재발송, 목적 구분·일회성·정확한 만료 시각·동시 확인·가입 실패 시 롤백을 확인합니다. SMTP 테스트는 실제 수신자에게 메일을 보내지 않습니다.

참조: [메일 발송 명세](https://app.notion.com/p/75a2dc037c3282a2b31981b37f5d46cc), [확인 명세](https://app.notion.com/p/d762dc037c32833a8e57015fc926d801). 30분/60초/10분은 대화에서 확정했습니다. 시연 전 직접 입력할 값은 [시연 전 체크리스트](pre-demo-checklist.md)에 정리했습니다.
