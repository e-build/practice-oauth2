plugins {
	kotlin("plugin.spring")
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	// OAuth2 Client 및 Resource Server 모두 지원
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

	// DB
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	runtimeOnly("com.mysql:mysql-connector-j")

//	 JWT 처리를 위한 의존성
//	implementation("org.springframework.security:spring-security-oauth2-jose")

	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test") // 추가
}