package me.practice.oauth2.utils

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PrincipalMaskingUtilTest {

    @Test
    @DisplayName("maskPrincipal은 이메일을 올바르게 마스킹한다")
    fun maskPrincipalShouldMaskEmailCorrectly() {
        assertEquals("t**t@example.com", PrincipalMaskingUtil.maskPrincipal("test@example.com"))
        assertEquals("u**r@example.com", PrincipalMaskingUtil.maskPrincipal("user@example.com"))
        assertEquals("**@example.com", PrincipalMaskingUtil.maskPrincipal("ab@example.com"))
        assertEquals("*@example.com", PrincipalMaskingUtil.maskPrincipal("a@example.com"))
    }

    @Test
    @DisplayName("maskPrincipal은 긴 이메일을 올바르게 마스킹한다")
    fun maskPrincipalShouldMaskLongEmailCorrectly() {
        assertEquals("v***********l@example.com", PrincipalMaskingUtil.maskPrincipal("verylongemail@example.com"))
    }

    @Test
    @DisplayName("maskPrincipal은 하이픈이 있는 전화번호를 올바르게 마스킹한다")
    fun maskPrincipalShouldMaskPhoneNumberWithHyphensCorrectly() {
        assertEquals("010-****-5678", PrincipalMaskingUtil.maskPrincipal("010-1234-5678"))
        assertEquals("010-***-5678", PrincipalMaskingUtil.maskPrincipal("010-123-5678"))
    }

    @Test
    @DisplayName("maskPrincipal은 하이픈이 없는 전화번호를 올바르게 마스킹한다")
    fun maskPrincipalShouldMaskPhoneNumberWithoutHyphensCorrectly() {
        assertEquals("010-****-5678", PrincipalMaskingUtil.maskPrincipal("01012345678"))
        assertEquals("010-***-5678", PrincipalMaskingUtil.maskPrincipal("0101235678"))
    }

    @Test
    @DisplayName("maskPrincipal은 일반 문자열을 올바르게 마스킹한다")
    fun maskPrincipalShouldMaskGeneralStringCorrectly() {
        assertEquals("**", PrincipalMaskingUtil.maskPrincipal("ab"))
        assertEquals("a**", PrincipalMaskingUtil.maskPrincipal("abc"))
        assertEquals("a***", PrincipalMaskingUtil.maskPrincipal("abcd"))
        assertEquals("te**st", PrincipalMaskingUtil.maskPrincipal("testst"))
        assertEquals("te***ng", PrincipalMaskingUtil.maskPrincipal("testing"))
    }

    @Test
    @DisplayName("maskPrincipal은 단일 문자를 처리할 수 있다")
    fun maskPrincipalShouldHandleSingleCharacter() {
        assertEquals("*", PrincipalMaskingUtil.maskPrincipal("a"))
    }

    @Test
    @DisplayName("maskPrincipal은 빈 문자열을 처리할 수 있다")
    fun maskPrincipalShouldHandleEmptyString() {
        assertEquals("", PrincipalMaskingUtil.maskPrincipal(""))
    }

    @Test
    @DisplayName("maskPrincipal은 유니코드 문자를 처리할 수 있다")
    fun maskPrincipalShouldHandleUnicodeCharacters() {
        assertEquals("한**", PrincipalMaskingUtil.maskPrincipal("한국글"))
        assertEquals("테**", PrincipalMaskingUtil.maskPrincipal("테스트"))
    }

    @Test
    @DisplayName("maskPrincipal은 혼합 형식 문자열을 처리할 수 있다")
    fun maskPrincipalShouldHandleMixedFormatStrings() {
        assertEquals("te**34", PrincipalMaskingUtil.maskPrincipal("test34"))
        assertEquals("ab***fg", PrincipalMaskingUtil.maskPrincipal("abcdefg"))
    }

    @Test
    @DisplayName("maskPrincipal은 잘못된 이메일 형식을 일반 문자열로 처리한다")
    fun maskPrincipalShouldHandleInvalidEmailFormatAsGeneralString() {
        assertEquals("in***id", PrincipalMaskingUtil.maskPrincipal("invalid"))
        assertEquals("te*t@", PrincipalMaskingUtil.maskPrincipal("test@"))
        assertEquals("@e****le", PrincipalMaskingUtil.maskPrincipal("@example"))
    }

    @Test
    @DisplayName("maskPrincipal은 잘못된 전화번호 형식을 일반 문자열로 처리한다")
    fun maskPrincipalShouldHandleInvalidPhoneFormatAsGeneralString() {
        assertEquals("12*45", PrincipalMaskingUtil.maskPrincipal("12345"))
        assertEquals("01**78", PrincipalMaskingUtil.maskPrincipal("010678"))
    }
}