Keycloak dev files generated for local SSO testing.

1) From this folder, run:
   docker compose up -d

2) Admin console:
   http://localhost:8081/
   admin / admin

3) Realm imported on boot:
   Realm name: shopl-sandbox
   Client (OIDC confidential): as-broker
   Test user: alice / password

4) OIDC endpoints (for Spring):
   Issuer URI: http://localhost:8081/realms/shopl-sandbox
   Discovery:   http://localhost:8081/realms/shopl-sandbox/.well-known/openid-configuration
