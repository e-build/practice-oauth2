plugins {
	kotlin("plugin.spring")
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-authorization-server")

	// 토큰 해시(인덱스에서 사용할 SHA-256 등)
	implementation("commons-codec:commons-codec:1.16.0")

	// DB
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	runtimeOnly("com.mysql:mysql-connector-j")

	// Nimbus JOSE + JWT 라이브러리 (JWK, JWT 처리용)
	implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
	
	// 프론트엔드 페이지를 위한 Thymeleaf 추가
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}