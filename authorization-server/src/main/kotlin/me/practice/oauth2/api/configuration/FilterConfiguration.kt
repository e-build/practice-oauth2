package me.practice.oauth2.api.configuration

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FilterConfiguration {

	@Bean
	fun mdcFilterRegistration(mdcFilter: MDCFilter): FilterRegistrationBean<MDCFilter> {
		return FilterRegistrationBean<MDCFilter>().apply {
			this.filter = mdcFilter
			this.order = Integer.MIN_VALUE
			this.addUrlPatterns("/*")
		}
	}
}