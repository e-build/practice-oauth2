package me.practice.oauth2.api.configuration

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class MDCFilter : OncePerRequestFilter() {

	override fun doFilterInternal(
		request: HttpServletRequest,
		response: HttpServletResponse,
		filterChain: FilterChain,
	) {
		try {
			// 요청 ID 생성 및 MDC에 추가
			val requestId = UUID.randomUUID().toString().replace("-", "")
			MDC.put("rId", requestId)

			// 다음 필터로 진행
			filterChain.doFilter(request, response)
		} finally {
			// 요청 완료 후 MDC 정리
			MDC.clear()
		}
	}
}