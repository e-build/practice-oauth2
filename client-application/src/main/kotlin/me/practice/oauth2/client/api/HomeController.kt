package me.practice.oauth2.client.api

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class HomeController {

    @GetMapping("/")
    fun index(): String {
        return "index"
    }

    @GetMapping("/login")
    fun login(): String {
        return "login"
    }

    @GetMapping("/home")
    fun home(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        model: Model
    ): String {
        println(">>> authenticated user = ${oauth2User?.name}")
        oauth2User?.let {
            model.addAttribute("name", it.getAttribute<String>("name") ?: it.name)
            model.addAttribute("authorities", it.authorities)
            model.addAttribute("attributes", it.attributes)
        }
        return "home"
    }
}