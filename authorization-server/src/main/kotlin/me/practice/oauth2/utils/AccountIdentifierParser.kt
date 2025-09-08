package me.practice.oauth2.utils

import me.practice.oauth2.domain.AccountIdentifierType
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * 사용자 식별자(username) 타입을 판단하는 유틸리티 클래스
 */
@Component
class AccountIdentifierParser {
    
    // 이메일 정규식 (RFC 5322 기준 단순화)
    private val EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    )
    
    // 휴대폰 번호 정규식 (한국 휴대폰 번호 패턴)
    // 010-1234-5678, 01012345678, +82-10-1234-5678 등을 지원
    private val PHONE_PATTERN = Pattern.compile(
        "^(\\+82[-\\s]?)?0?1[016789][-\\s]?\\d{3,4}[-\\s]?\\d{4}$"
    )
    
    /**
     * 주어진 식별자가 이메일 형식인지 확인
     */
    fun isEmail(identifier: String): Boolean {
        return EMAIL_PATTERN.matcher(identifier.trim()).matches()
    }
    
    /**
     * 주어진 식별자가 휴대폰 번호 형식인지 확인
     */
    fun isPhoneNumber(identifier: String): Boolean {
        // 공백과 하이픈 제거 후 검증
        val cleanIdentifier = identifier.replace("\\s".toRegex(), "").replace("-", "")
        return PHONE_PATTERN.matcher(identifier.trim()).matches() || 
               isSimplePhoneNumber(cleanIdentifier)
    }
    
    /**
     * 단순한 숫자 패턴의 휴대폰 번호 확인 (010으로 시작하는 11자리)
     */
    private fun isSimplePhoneNumber(identifier: String): Boolean {
        return identifier.matches("^01[016789]\\d{7,8}$".toRegex())
    }
    
    /**
     * 휴대폰 번호를 표준 형태로 정규화
     * 예: "010-1234-5678" -> "01012345678"
     */
    fun normalizePhoneNumber(phoneNumber: String): String {
        return phoneNumber.replace("\\s".toRegex(), "")
            .replace("-", "")
            .replace("+82", "0")
    }
    
    /**
     * 사용자 식별자의 타입을 반환
     */
    fun parse(identifier: String): AccountIdentifierType {
        return when {
            isEmail(identifier) -> AccountIdentifierType.EMAIL
            isPhoneNumber(identifier) -> AccountIdentifierType.PHONE
            else -> AccountIdentifierType.UNKNOWN
        }
    }
    

}