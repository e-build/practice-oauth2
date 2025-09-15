/*
 * ===== DEPRECATED =====
 * 이 파일의 내용은 다음과 같이 분리되었습니다:
 *
 * 1. DTO 클래스들 -> infrastructure/redis/dto/RedisAuthorizationDTO.kt
 * 2. 변환 로직 -> infrastructure/redis/converter/OAuth2AuthorizationConverter.kt
 * 3. 속성 복원 로직 -> infrastructure/redis/converter/OAuth2AttributeCoercer.kt
 * 4. SSO 계정 재구성 -> infrastructure/redis/reconstructor/SsoAccountReconstructor.kt
 * 5. 제공자별 사용자 ID 추출 -> infrastructure/redis/extractor/ProviderUserIdExtractor.kt와 구현체들
 *
 * 이 파일은 리팩토링 완료 후 삭제 예정입니다.
 */
