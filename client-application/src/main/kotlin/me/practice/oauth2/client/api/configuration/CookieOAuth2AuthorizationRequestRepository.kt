package me.practice.oauth2.client.api.configuration

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.stereotype.Component

@Component
class CookieOAuth2AuthorizationRequestRepository: AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

	companion object {
		private const val OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request"
		private const val REDIRECT_URI_PARAM_COOKIE_NAME = "redirect_uri"
		private const val COOKIE_EXPIRE_SECONDS = 180  // 3분
	}

	override fun loadAuthorizationRequest(request: HttpServletRequest): OAuth2AuthorizationRequest? {
		return CookieUtils.getDeserializedCookie<OAuth2AuthorizationRequest>(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME)
	}

	override fun saveAuthorizationRequest(
		authorizationRequest: OAuth2AuthorizationRequest?,
		request: HttpServletRequest,
		response: HttpServletResponse
	) {
		if (authorizationRequest == null) {
			CookieUtils.deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME)
			CookieUtils.deleteCookie(request, response, REDIRECT_URI_PARAM_COOKIE_NAME)
			return
		}

		// Authorization Request 저장
		CookieUtils.addSerializedCookie(response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, authorizationRequest, COOKIE_EXPIRE_SECONDS)

		// Redirect URI 저장 (필요시)
		val redirectUriAfterLogin = request.getParameter("redirect_uri")
		if (redirectUriAfterLogin?.isNotBlank() == true) {
			CookieUtils.addSimpleCookie(response, REDIRECT_URI_PARAM_COOKIE_NAME, redirectUriAfterLogin, COOKIE_EXPIRE_SECONDS)
		}
	}

	override fun removeAuthorizationRequest(
		request: HttpServletRequest,
		response: HttpServletResponse
	): OAuth2AuthorizationRequest? {
		return loadAuthorizationRequest(request).also {
			CookieUtils.deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME)
			CookieUtils.deleteCookie(request, response, REDIRECT_URI_PARAM_COOKIE_NAME)
		}
	}

	fun getRedirectUri(request: HttpServletRequest): String? {
		return CookieUtils.getSimpleCookieValue(request, REDIRECT_URI_PARAM_COOKIE_NAME)
	}
}