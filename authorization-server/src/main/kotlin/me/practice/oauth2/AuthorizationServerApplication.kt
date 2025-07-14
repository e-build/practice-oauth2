package me.practice.oauth2

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AuthorizationServerApplication

fun main(args: Array<String>) {
	runApplication<AuthorizationServerApplication>(*args)
}
