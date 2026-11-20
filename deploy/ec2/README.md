# 서울 EC2 시연 배포

이 설정은 `main`에 푸시된 최신 커밋의 단위·MySQL 통합 테스트가 통과하면 실행 JAR을 배포합니다. 서버의 HTTPS 도메인과 MySQL, OAuth, SMTP 값은 EC2 실행 환경에서 관리합니다.

1. 서울 리전의 CloudFormation에서 `aws-stack.yml`을 업로드합니다. `InstanceId`에는 시연용 EC2 ID를 지정합니다. 이 계정에 GitHub OIDC 공급자가 이미 있다면 중복 생성하기 전에 템플릿의 공급자 참조를 기존 ARN으로 바꿉니다.
2. 스택 출력의 `DemoEc2InstanceProfile`을 EC2에 연결하고 SSM 관리 노드에 온라인으로 표시되는지 확인합니다. EC2에는 Java 21, AWS CLI v2, `curl`, `flock`을 준비합니다.
3. 아래 명령은 EC2에서 실행합니다. 파일은 이 저장소의 `deploy/ec2` 디렉터리에서 가져옵니다.

   ```bash
   sudo useradd --system --no-create-home --shell /usr/sbin/nologin maeum
   sudo install -d -m 0755 /opt/maeum
   sudo install -d -m 0700 /etc/maeum
   sudo install -m 0644 maeum-backend.service /etc/systemd/system/maeum-backend.service
   sudo install -m 0755 deploy-backend.sh /usr/local/sbin/maeum-deploy
   sudo systemctl daemon-reload
   sudo systemctl enable maeum-backend.service
   ```

4. `/etc/maeum/deploy.conf`에 `DEPLOY_BUCKET`을 스택의 `DemoDeployBucket` 출력으로 설정합니다. `/etc/maeum/backend.env`에는 [.env.example](../../.env.example)을 기준으로 실제 실행 값을 채웁니다. 두 파일은 root 소유로 만들고 권한을 `0600`으로 제한합니다. Spring Boot는 `127.0.0.1:8080`에서 수신하므로 외부 HTTPS 접근은 리버스 프록시에서 연결합니다.
5. GitHub 저장소의 **Settings → Secrets and variables → Actions → Variables**에서 `DEMO_AWS_ROLE_ARN`, `DEMO_DEPLOY_BUCKET`, `DEMO_EC2_INSTANCE_ID`를 등록합니다. 위 서버 설정을 마친 뒤 `DEMO_DEPLOY_ENABLED`를 `true`로 설정하고 `main`에 변경을 머지합니다. OAuth와 SMTP 비밀값은 GitHub Variables에 넣지 않습니다.
6. Actions에서 배포 성공을 확인하고 `curl -fsS https://<백엔드-도메인>/actuator/health`와 로그인 시연을 확인합니다. 실패 시 Actions의 SSM 실행 결과와 EC2의 `sudo journalctl -u maeum-backend.service -n 80 --no-pager`를 확인합니다.

새 JAR의 헬스 체크가 실패하면 서버에서 이전 JAR을 다시 실행합니다. 이때 Flyway가 이미 적용한 DB 변경은 되돌아가지 않으므로, 시연 전 마이그레이션은 이전 JAR과 함께 동작할 수 있게 검토합니다.
