package com.etfmonitor.core.database

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Converters 단위 테스트
 *
 * 테스트 범위:
 * - fromStringList / toStringList: 라운드트립, 빈 리스트, null/빈 문자열, 잘못된 JSON, 특수문자
 * - fromLongList / toLongList: 라운드트립, 빈 리스트, 잘못된 JSON, 단일 요소
 */
@DisplayName("Converters 테스트")
class ConvertersTest {

    private lateinit var converters: Converters

    @BeforeEach
    fun setup() {
        converters = Converters()
    }

    // =========================================================================
    // String list 라운드트립
    // =========================================================================

    @Nested
    @DisplayName("String 리스트 변환 테스트")
    inner class StringListTests {

        @Test
        @DisplayName("String 리스트 → JSON → String 리스트 라운드트립이 원본을 보존한다")
        fun `fromStringList toStringList roundtrip preserves list`() {
            val original = listOf("삼성전자", "SK하이닉스", "LG에너지솔루션")

            val json = converters.fromStringList(original)
            val restored = converters.toStringList(json)

            assertEquals(original, restored)
        }

        @Test
        @DisplayName("빈 String 리스트는 빈 리스트로 복원된다")
        fun `fromStringList toStringList empty list roundtrip`() {
            val original = emptyList<String>()

            val json = converters.fromStringList(original)
            val restored = converters.toStringList(json)

            assertTrue(restored.isEmpty())
        }

        @Test
        @DisplayName("단일 요소 String 리스트는 올바르게 라운드트립된다")
        fun `fromStringList toStringList single element roundtrip`() {
            val original = listOf("069500")

            val json = converters.fromStringList(original)
            val restored = converters.toStringList(json)

            assertEquals(original, restored)
        }

        @Test
        @DisplayName("특수문자가 포함된 String 리스트도 올바르게 라운드트립된다")
        fun `fromStringList toStringList with special characters roundtrip`() {
            val original = listOf("hello \"world\"", "tab\there", "new\nline", "한국어")

            val json = converters.fromStringList(original)
            val restored = converters.toStringList(json)

            assertEquals(original, restored)
        }

        @Test
        @DisplayName("빈 문자열 요소가 포함된 리스트도 라운드트립된다")
        fun `fromStringList toStringList with empty string element roundtrip`() {
            val original = listOf("", "second", "")

            val json = converters.fromStringList(original)
            val restored = converters.toStringList(json)

            assertEquals(original, restored)
        }

        @Test
        @DisplayName("빈 문자열 입력은 toStringList에서 빈 리스트를 반환한다")
        fun `toStringList with empty string returns emptyList`() {
            val result = converters.toStringList("")

            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("null JSON 문자열(빈 입력)은 toStringList에서 빈 리스트를 반환한다")
        fun `toStringList with plain text returns emptyList`() {
            val result = converters.toStringList("not json at all")

            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("잘못된 JSON은 toStringList에서 빈 리스트를 반환한다")
        fun `toStringList with malformed JSON returns emptyList`() {
            val result = converters.toStringList("{invalid json}")

            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("JSON 객체(배열이 아님)는 toStringList에서 빈 리스트를 반환한다")
        fun `toStringList with JSON object not array returns emptyList`() {
            val result = converters.toStringList("{\"key\": \"value\"}")

            // JSONArray("{\"key\":\"value\"}")는 JSONException → emptyList
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("100개 요소 String 리스트 라운드트립이 올바르게 동작한다")
        fun `fromStringList toStringList with 100 elements roundtrip`() {
            val original = (1..100).map { "ticker_$it" }

            val json = converters.fromStringList(original)
            val restored = converters.toStringList(json)

            assertEquals(original, restored)
        }
    }

    // =========================================================================
    // Long list 라운드트립
    // =========================================================================

    @Nested
    @DisplayName("Long 리스트 변환 테스트")
    inner class LongListTests {

        @Test
        @DisplayName("Long 리스트 → JSON → Long 리스트 라운드트립이 원본을 보존한다")
        fun `fromLongList toLongList roundtrip preserves list`() {
            val original = listOf(1L, 100L, 1_000_000L, 9_999_999_999L)

            val json = converters.fromLongList(original)
            val restored = converters.toLongList(json)

            assertEquals(original, restored)
        }

        @Test
        @DisplayName("빈 Long 리스트는 빈 리스트로 복원된다")
        fun `fromLongList toLongList empty list roundtrip`() {
            val original = emptyList<Long>()

            val json = converters.fromLongList(original)
            val restored = converters.toLongList(json)

            assertTrue(restored.isEmpty())
        }

        @Test
        @DisplayName("단일 요소 Long 리스트는 올바르게 라운드트립된다")
        fun `fromLongList toLongList single element roundtrip`() {
            val original = listOf(42L)

            val json = converters.fromLongList(original)
            val restored = converters.toLongList(json)

            assertEquals(original, restored)
        }

        @Test
        @DisplayName("0과 음수 Long 값도 올바르게 라운드트립된다")
        fun `fromLongList toLongList zero and negative values roundtrip`() {
            val original = listOf(0L, -1L, -1_000_000L, Long.MIN_VALUE)

            val json = converters.fromLongList(original)
            val restored = converters.toLongList(json)

            assertEquals(original, restored)
        }

        @Test
        @DisplayName("Long.MAX_VALUE도 올바르게 라운드트립된다")
        fun `fromLongList toLongList Long MAX_VALUE roundtrip`() {
            val original = listOf(Long.MAX_VALUE)

            val json = converters.fromLongList(original)
            val restored = converters.toLongList(json)

            assertEquals(original, restored)
        }

        @Test
        @DisplayName("잘못된 JSON은 toLongList에서 빈 리스트를 반환한다")
        fun `toLongList with malformed JSON returns emptyList`() {
            val result = converters.toLongList("not valid json")

            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("빈 문자열은 toLongList에서 빈 리스트를 반환한다")
        fun `toLongList with empty string returns emptyList`() {
            val result = converters.toLongList("")

            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("JSON 객체(배열이 아님)는 toLongList에서 빈 리스트를 반환한다")
        fun `toLongList with JSON object not array returns emptyList`() {
            val result = converters.toLongList("{\"key\": 123}")

            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("fromLongList는 올바른 JSON 배열 문자열을 생성한다")
        fun `fromLongList produces valid JSON array string`() {
            val json = converters.fromLongList(listOf(1L, 2L, 3L))

            // JSON 배열 형식 확인
            assertTrue(json.startsWith("["), "Should start with '[' but was: $json")
            assertTrue(json.endsWith("]"), "Should end with ']' but was: $json")
        }

        @Test
        @DisplayName("timestamp 형식의 Long 값 리스트도 올바르게 라운드트립된다")
        fun `fromLongList toLongList timestamp list roundtrip`() {
            val original = listOf(1708300800000L, 1708387200000L, 1708473600000L)

            val json = converters.fromLongList(original)
            val restored = converters.toLongList(json)

            assertEquals(original, restored)
        }
    }
}
