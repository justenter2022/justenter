
package justenter.common.enum

/**
 * CODE128 바코드 타입
 * - CODE128A: 대문자, 숫자, 제어문자
 * - CODE128B: 대소문자, 숫자, 특수문자
 * - CODE128C: 숫자 쌍 (가장 효율적)
 */
enum class Code128Type {
    CODE128A,
    CODE128B,
    CODE128C
}
