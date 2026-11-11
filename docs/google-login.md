# Google 로그인·세션 API

## 구현 범위

GIS JavaScript callback의 ID Token을 서버가 Google 공개키로 검증합니다. `sub`로 Google 계정을 식별하며 이메일이 같은 일반 회원과 자동 연결하지 않습니다. 신규 회원은 필수 약관 동의와 현재 문서 버전이 필요합니다. 닉네임·프로필 완료·온보딩 완료는 초기 미완료입니다. 다른 도메인의 양털 지갑 생성은 이번 범위에 포함하지 않습니다.

`mysql,auth` 프로필을 사용하며 이메일 인증 발송도 필요하면 `smtp`를 추가합니다. 필요한 환경변수는 [시연 체크리스트](pre-demo-checklist.md)에 있습니다. 실제 설정 누락 시 인증 프로필 기동이 실패합니다. bootstrap은 여전히 DB 없는 기동 확인 전용입니다.

## API와 프론트 순서

모든 아래 POST는 정확한 허용 `Origin`과 `credentials: include`가 필요합니다. Origin 누락도 403입니다. 브라우저가 쿠키를 관리하며 프론트가 Refresh Token 값을 읽지 않습니다. 토큰 발급 응답에는 `Cache-Control: no-store`를 설정합니다.

| 순서 | Method·Path | 요청·응답 |
| --- | --- | --- |
| 1 | `POST /api/v1/auth/google/challenges` | 본문 없음. 200 `data: {nonce, expiresIn: 300}`. 로그인 challenge 쿠키 발급 |
| 2 | Google Identity Services 초기화 | 같은 Client ID와 위 `nonce`를 설정. JS callback으로 ID Token 수신 |
| 3 | `POST /api/v1/auth/google` | 아래 JSON. 기존 회원 200, 신규 회원 201. Refresh 쿠키 설정 및 challenge 쿠키 삭제 |
| 4 | `POST /api/v1/auth/refresh` | 본문 없음. Refresh 쿠키로 재발급. 200 `data: {accessToken, tokenType: "Bearer", expiresIn: 3600}` |
| 5 | `POST /api/v1/auth/logout` | 본문 없음. 해당 세션 폐기 및 인증 쿠키 삭제. 반복 요청도 204 |

로그인 JSON 예시의 v1은 테스트용입니다. 실제 문서 버전으로 바꿉니다.

```json
{
  "credential": "GIS callback의 ID Token",
  "termsVersion": "v1",
  "termsAgreed": true,
  "privacyVersion": "v1",
  "privacyAgreed": true,
  "marketingVersion": "v1",
  "marketingAgreed": false
}
```

기존 회원 로그인에는 credential만 필요합니다. 신규 회원이 동의 없이 요청하면 `409 AUTH_SIGNUP_CONSENT_REQUIRED`이며 아직 회원이 생성되지 않습니다. 동의 화면을 표시하고 동일한 challenge·credential로 유효시간 안에 재요청할 수 있습니다. 만료 시 challenge와 Google credential을 새로 받습니다. 약관 버전 불일치·필수 미동의는 `400 AUTH_REQUIRED_CONSENT_MISSING`입니다.

로그인 성공 응답:

```json
{
  "data": {
    "accessToken": "서비스 Access Token",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "userId": "123",
      "nickname": null,
      "profileCompleted": false,
      "onboardingCompleted": false
    },
    "isNewUser": true
  }
}
```

Google ID Token은 서비스 Access Token으로 사용할 수 없습니다. 로그인 이후 업무 API에는 서비스 Access Token을 메모리에 보관하여 `Authorization: Bearer ...`로 전달합니다. 재발급·로그아웃에는 Access Token이 필요 없으며 만료된 Authorization 헤더가 있어도 Refresh 쿠키로 처리합니다.

## 검증·보관 정책

- Google 서명, 발급자, Client ID 대상, 만료, 발급 시각, 검증된 이메일, nonce 연결을 확인합니다. 이메일은 연락용이며 계정 통합·일반 로그인 권한의 근거가 아닙니다.
- challenge는 5분·일회용이며 DB에는 해시만 저장합니다. 성공한 로그인에서만 소비합니다. 발급 요청마다 새 challenge 쿠키로 교체됩니다.
- 로그인·쿠키 동작은 서버의 정확한 Origin 허용 목록으로 보호합니다. GIS redirect POST를 직접 받는 구현이 아닙니다.
- 서비스 Access Token은 HS256, 1시간입니다. 발급자·대상·용도·만료·세션 폐기 상태를 검증합니다.
- Refresh Token은 32바이트 난수, DB에는 SHA-256 해시만 저장합니다. 매 발급 시점부터 14일이며 회전할 때마다 만료를 갱신합니다.
- 쿠키는 HttpOnly·Secure·설정한 SameSite, Domain 생략으로 호스트에 한정합니다. Refresh Path는 `/api/v1/auth`, challenge Path는 `/api/v1/auth/google`입니다.
- 사용한 Refresh Token을 다시 사용하면 해당 세션 전체를 폐기합니다. 현재 Access Token도 다음 API 인증부터 거부합니다. 폐기는 롤백되지 않습니다.
- 로그인할 때마다 별도 세션을 생성합니다. 로그아웃은 해당 세션에만 적용하며 다른 기기의 세션까지 폐기하지 않습니다.
- Access Token 검증마다 세션 상태 DB 조회가 발생합니다. 즉시 로그아웃을 위한 선택이며 현재 규모에서 사용합니다.
- 동시 신규 로그인은 Google sub 단위 잠금으로 중복 회원 생성을 방지합니다. 재발급도 세션 잠금으로 직렬 처리합니다.

프론트는 재발급을 single flight로 처리하고 여러 탭도 조정해야 합니다. 같은 Refresh Token의 동시 요청은 하나만 성공하고 다른 요청은 재사용으로 감지되어 세션이 폐기됩니다. 응답 유실 후 이미 사용한 쿠키로 무조건 재시도하는 경우에도 재로그인이 필요할 수 있습니다.

## 오류

| HTTP | code | 의미 |
| --- | --- | --- |
| 400 | `AUTH_REQUIRED_CONSENT_MISSING` | 필수 동의·문서 버전 확인 |
| 401 | `AUTH_GOOGLE_TOKEN_INVALID` | Google ID Token 검증 실패 |
| 401 | `AUTH_GOOGLE_CHALLENGE_INVALID` | challenge 누락·만료·이미 사용됨 |
| 401 | `AUTH_ACCESS_TOKEN_INVALID` / `AUTH_ACCESS_TOKEN_EXPIRED` | 서비스 토큰 오류·만료 |
| 401 | `AUTH_REFRESH_TOKEN_INVALID` / `AUTH_REFRESH_TOKEN_EXPIRED` / `AUTH_REFRESH_TOKEN_REUSED` | 재로그인 필요. Refresh 쿠키 삭제 |
| 403 | `AUTH_CSRF_FAILED` | 쿠키 API의 Origin 누락. 허용하지 않은 Origin은 앞선 CORS 필터가 403으로 차단할 수도 있음 |
| 409 | `AUTH_SIGNUP_CONSENT_REQUIRED` | 신규 회원 동의 화면 필요 |

## DB·검증·남은 작업

V3에서 social_accounts, user_consents, auth_identity_locks, google_login_challenges, auth_sessions, refresh_tokens를 추가합니다. 적용된 V1·V2는 변경하지 않습니다. 일반 회원가입도 같은 동의·세션 구조를 사용합니다.

자동 테스트는 로컬 RSA 키로 Google JWT 검증을 확인하고, 별도 MySQL 8.0 DB에서 신규/기존 로그인·동의 롤백·계정 분리·중복 가입·동시 재발급·재사용 폐기·만료·로그아웃·Origin 차단을 확인합니다. 통합 테스트의 Google 검증기는 테스트 대체 구현이며 실제 Google 계정 연동 검증을 대체하지 않습니다.

아직 필요한 작업은 실제 Client ID·Origin·JWT 키·약관 설정, QR 실기기 연동, 프로필·온보딩 API, 만료 challenge와 세션/Refresh 이력의 보관 기간·정리 작업입니다. 재사용 감지에 필요한 이전 Refresh 이력을 활성 세션 중 임의 삭제하지 않습니다.

공식 참고: [Google ID Token 서버 검증](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token), [GIS JavaScript nonce·callback](https://developers.google.com/identity/gsi/web/reference/js-reference).
