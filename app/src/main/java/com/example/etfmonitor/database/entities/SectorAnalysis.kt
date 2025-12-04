package com.etfmonitor.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 섹터별 Fear & Greed 분석 결과 저장
 */
@Entity(
    tableName = "sector_analysis",
    indices = [
        Index(value = ["date"]),
        Index(value = ["sector"]),
        Index(value = ["sector", "date"])
    ]
)
data class SectorAnalysis(
    @PrimaryKey
    val id: String,  // "{sector}-{date}"
    val sector: String,
    val sectorName: String,  // 한글 섹터명
    val date: String,
    val fearGreedValue: Double,      // 0.0 ~ 1.0
    val etfFlowScore: Double,        // -1.0 ~ 1.0
    val momentumScore: Double,       // -1.0 ~ 1.0
    val volatilityScore: Double,     // 0.0 ~ 1.0
    val stockCount: Int,             // 섹터 내 종목 수
    val newEntries: Int,             // 신규 ETF 편입 수
    val removals: Int,               // ETF 제외 수
    val avgWeightChange: Double,     // 평균 비중 변화 (%)
    val sentiment: String,           // EXTREME_GREED, GREED, NEUTRAL, FEAR, EXTREME_FEAR
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        fun createId(sector: String, date: String) = "$sector-$date"
    }
}

/**
 * 섹터별 심리 상태
 */
enum class SectorSentiment(val displayName: String) {
    EXTREME_GREED("극도 탐욕"),
    GREED("탐욕"),
    NEUTRAL("중립"),
    FEAR("공포"),
    EXTREME_FEAR("극도 공포");

    companion object {
        fun fromValue(value: Double): SectorSentiment = when {
            value > 0.8 -> EXTREME_GREED
            value > 0.6 -> GREED
            value > 0.4 -> NEUTRAL
            value > 0.2 -> FEAR
            else -> EXTREME_FEAR
        }
    }
}

/**
 * 섹터 분류 매핑
 *
 * 분류 우선순위:
 * 1. 종목 티커 기반 (주요 대형주 직접 매핑)
 * 2. 종목명 키워드 기반 (패턴 매칭)
 * 3. 기본값: OTHER
 */
object SectorMapping {
    val SECTOR_NAMES = mapOf(
        "SEMICONDUCTOR" to "반도체",
        "BATTERY" to "2차전지",
        "BIO" to "바이오/헬스케어",
        "AUTO" to "자동차/모빌리티",
        "FINANCE" to "금융",
        "IT" to "IT/소프트웨어",
        "CHEMICAL" to "화학",
        "STEEL" to "철강/금속",
        "CONSTRUCTION" to "건설",
        "RETAIL" to "유통/소비재",
        "ENTERTAINMENT" to "엔터/미디어",
        "ENERGY" to "에너지/유틸리티",
        "TELECOM" to "통신",
        "SHIPBUILDING" to "조선/해운",
        "DEFENSE" to "방산/항공",
        "OTHER" to "기타"
    )

    /**
     * 주요 종목 티커별 섹터 매핑 (KOSPI/KOSDAQ 대형주)
     * 종목 티커로 직접 매핑하여 정확도 향상
     */
    private val TICKER_TO_SECTOR = mapOf(
        // 반도체
        "005930" to "SEMICONDUCTOR",  // 삼성전자
        "000660" to "SEMICONDUCTOR",  // SK하이닉스
        "042700" to "SEMICONDUCTOR",  // 한미반도체
        "403870" to "SEMICONDUCTOR",  // HPSP
        "058470" to "SEMICONDUCTOR",  // 리노공업
        "357780" to "SEMICONDUCTOR",  // 솔브레인
        "036830" to "SEMICONDUCTOR",  // 솔브레인홀딩스
        "086390" to "SEMICONDUCTOR",  // 유니테스트
        "095340" to "SEMICONDUCTOR",  // ISC
        "006400" to "SEMICONDUCTOR",  // 삼성SDI (반도체소재)
        "402340" to "SEMICONDUCTOR",  // SK스퀘어

        // 2차전지
        "373220" to "BATTERY",  // LG에너지솔루션
        "051910" to "BATTERY",  // LG화학
        "247540" to "BATTERY",  // 에코프로비엠
        "086520" to "BATTERY",  // 에코프로
        "003670" to "BATTERY",  // 포스코퓨처엠
        "012450" to "BATTERY",  // 한화에어로스페이스 (2차전지 장비)
        "006280" to "BATTERY",  // 녹십자
        "298040" to "BATTERY",  // 효성중공업
        "196170" to "BATTERY",  // 알테오젠
        "217880" to "BATTERY",  // 삼현철강

        // 바이오/헬스케어
        "068270" to "BIO",  // 셀트리온
        "207940" to "BIO",  // 삼성바이오로직스
        "326030" to "BIO",  // SK바이오팜
        "091990" to "BIO",  // 셀트리온헬스케어
        "302440" to "BIO",  // SK바이오사이언스
        "145020" to "BIO",  // 휴젤
        "328130" to "BIO",  // 루닛
        "141080" to "BIO",  // 레고켐바이오
        "950160" to "BIO",  // 코오롱티슈진
        "006110" to "BIO",  // 삼아알미늄
        "128940" to "BIO",  // 한미약품
        "000100" to "BIO",  // 유한양행
        "185750" to "BIO",  // 종근당

        // 자동차/모빌리티
        "005380" to "AUTO",  // 현대차
        "000270" to "AUTO",  // 기아
        "005387" to "AUTO",  // 현대차2우B
        "005385" to "AUTO",  // 현대차우
        "012330" to "AUTO",  // 현대모비스
        "011210" to "AUTO",  // 현대위아
        "004490" to "AUTO",  // 세방전지
        "161390" to "AUTO",  // 한국타이어앤테크놀로지
        "018880" to "AUTO",  // 한온시스템

        // 금융
        "105560" to "FINANCE",  // KB금융
        "055550" to "FINANCE",  // 신한지주
        "086790" to "FINANCE",  // 하나금융지주
        "316140" to "FINANCE",  // 우리금융지주
        "024110" to "FINANCE",  // 기업은행
        "000810" to "FINANCE",  // 삼성화재
        "032830" to "FINANCE",  // 삼성생명
        "138930" to "FINANCE",  // BNK금융지주
        "139130" to "FINANCE",  // DGB금융지주
        "071050" to "FINANCE",  // 한국금융지주
        "003550" to "FINANCE",  // LG
        "005940" to "FINANCE",  // NH투자증권
        "006800" to "FINANCE",  // 미래에셋증권
        "016360" to "FINANCE",  // 삼성증권
        "030200" to "FINANCE",  // KT

        // IT/소프트웨어
        "035720" to "IT",  // 카카오
        "035420" to "IT",  // NAVER
        "259960" to "IT",  // 크래프톤
        "263750" to "IT",  // 펄어비스
        "251270" to "IT",  // 넷마블
        "041510" to "IT",  // 에스엠
        "352820" to "IT",  // 하이브
        "293490" to "IT",  // 카카오게임즈
        "036570" to "IT",  // 엔씨소프트
        "030520" to "IT",  // 한글과컴퓨터
        "035900" to "IT",  // JYP Ent.

        // 화학
        "051900" to "CHEMICAL",  // LG화학 (화학부문)
        "010950" to "CHEMICAL",  // S-Oil
        "096770" to "CHEMICAL",  // SK이노베이션
        "011170" to "CHEMICAL",  // 롯데케미칼
        "011070" to "CHEMICAL",  // LG이노텍
        "006650" to "CHEMICAL",  // 대한유화
        "004000" to "CHEMICAL",  // 롯데정밀화학
        "001230" to "CHEMICAL",  // 동국제강
        "010060" to "CHEMICAL",  // OCI홀딩스
        "078930" to "CHEMICAL",  // GS
        "003490" to "CHEMICAL",  // 대한항공 (연료)
        "180640" to "CHEMICAL",  // 한진칼

        // 철강/금속
        "005490" to "STEEL",  // POSCO홀딩스
        "004020" to "STEEL",  // 현대제철
        "042660" to "STEEL",  // 한화오션
        "010130" to "STEEL",  // 고려아연
        "103140" to "STEEL",  // 풍산
        "001040" to "STEEL",  // CJ
        "004170" to "STEEL",  // 신세계
        "069960" to "STEEL",  // 현대백화점

        // 건설
        "000720" to "CONSTRUCTION",  // 현대건설
        "047040" to "CONSTRUCTION",  // 대우건설
        "006360" to "CONSTRUCTION",  // GS건설
        "028050" to "CONSTRUCTION",  // 삼성엔지니어링
        "000210" to "CONSTRUCTION",  // DL
        "034020" to "CONSTRUCTION",  // 두산에너빌리티
        "375500" to "CONSTRUCTION",  // DL이앤씨

        // 유통/소비재
        "051600" to "RETAIL",  // 한전KPS
        "090430" to "RETAIL",  // 아모레퍼시픽
        "002790" to "RETAIL",  // 아모레G
        "021240" to "RETAIL",  // 코웨이
        "034730" to "RETAIL",  // SK
        "097950" to "RETAIL",  // CJ제일제당
        "007070" to "RETAIL",  // GS리테일
        "004990" to "RETAIL",  // 롯데지주
        "023530" to "RETAIL",  // 롯데쇼핑
        "139480" to "RETAIL",  // 이마트
        "035760" to "RETAIL",  // CJ ENM

        // 엔터/미디어
        "041510" to "ENTERTAINMENT",  // 에스엠
        "352820" to "ENTERTAINMENT",  // 하이브
        "122870" to "ENTERTAINMENT",  // YG엔터테인먼트
        "035900" to "ENTERTAINMENT",  // JYP Ent.
        "101730" to "ENTERTAINMENT",  // 위지윅스튜디오
        "034230" to "ENTERTAINMENT",  // 파라다이스
        "079160" to "ENTERTAINMENT",  // CJ CGV

        // 에너지/유틸리티
        "015760" to "ENERGY",  // 한국전력
        "017670" to "ENERGY",  // SK텔레콤
        "036460" to "ENERGY",  // 한국가스공사
        "034020" to "ENERGY",  // 두산에너빌리티
        "267250" to "ENERGY",  // HD현대
        "009540" to "ENERGY",  // 한국조선해양
        "329180" to "ENERGY",  // HD현대중공업

        // 통신
        "030200" to "TELECOM",  // KT
        "017670" to "TELECOM",  // SK텔레콤
        "032640" to "TELECOM",  // LG유플러스

        // 조선/해운
        "009540" to "SHIPBUILDING",  // 한국조선해양
        "329180" to "SHIPBUILDING",  // HD현대중공업
        "042660" to "SHIPBUILDING",  // 한화오션
        "010620" to "SHIPBUILDING",  // 현대미포조선
        "011200" to "SHIPBUILDING",  // HMM
        "028670" to "SHIPBUILDING",  // 팬오션

        // 방산/항공
        "012450" to "DEFENSE",  // 한화에어로스페이스
        "047810" to "DEFENSE",  // 한국항공우주
        "003490" to "DEFENSE",  // 대한항공
        "089590" to "DEFENSE",  // 제주항공
        "180640" to "DEFENSE",  // 한진칼
        "012630" to "DEFENSE"   // 현대로템
    )

    /**
     * 종목명 키워드별 섹터 매핑 (패턴 매칭용)
     */
    private val NAME_PATTERNS = listOf(
        // 반도체
        listOf("반도체", "하이닉스", "마이크론", "메모리", "파운드리", "팹리스", "웨이퍼", "ASML") to "SEMICONDUCTOR",

        // 2차전지
        listOf("배터리", "2차전지", "이차전지", "리튬", "양극재", "음극재", "전해질", "분리막", "에너지솔루션") to "BATTERY",

        // 바이오
        listOf("바이오", "제약", "헬스케어", "신약", "임상", "세포", "유전자", "항체", "백신", "의료기기", "병원", "약품") to "BIO",

        // 자동차
        listOf("자동차", "모빌리티", "현대차", "기아", "모비스", "타이어", "전기차", "EV", "부품") to "AUTO",

        // 금융
        listOf("금융", "은행", "보험", "증권", "투자", "캐피탈", "카드", "저축", "지주") to "FINANCE",

        // IT
        listOf("카카오", "네이버", "게임", "소프트웨어", "플랫폼", "인터넷", "IT", "데이터", "클라우드", "AI", "인공지능") to "IT",

        // 화학
        listOf("화학", "정유", "석유", "화공", "페인트", "수지", "플라스틱", "고무") to "CHEMICAL",

        // 철강
        listOf("철강", "금속", "포스코", "제철", "알루미늄", "동", "아연", "니켈") to "STEEL",

        // 건설
        listOf("건설", "건축", "시공", "인프라", "토목", "플랜트", "엔지니어링") to "CONSTRUCTION",

        // 유통/소비재
        listOf("유통", "소비재", "마트", "백화점", "쇼핑", "리테일", "식품", "음료", "화장품") to "RETAIL",

        // 엔터/미디어
        listOf("엔터", "미디어", "방송", "콘텐츠", "영화", "음악", "기획사", "드라마") to "ENTERTAINMENT",

        // 에너지
        listOf("에너지", "전력", "가스", "신재생", "태양광", "풍력", "수소", "유틸리티") to "ENERGY",

        // 통신
        listOf("통신", "텔레콤", "5G", "네트워크", "모바일") to "TELECOM",

        // 조선
        listOf("조선", "해운", "선박", "해양", "중공업") to "SHIPBUILDING",

        // 방산
        listOf("방산", "방위", "항공", "우주", "미사일", "무기") to "DEFENSE"
    )

    /**
     * ETF 이름에서 섹터 추론
     */
    fun inferSectorFromEtfName(etfName: String): String {
        val nameLower = etfName.lowercase()
        return when {
            nameLower.contains("반도체") || nameLower.contains("시스템반도체") -> "SEMICONDUCTOR"
            nameLower.contains("2차전지") || nameLower.contains("배터리") || nameLower.contains("전기차") -> "BATTERY"
            nameLower.contains("바이오") || nameLower.contains("헬스케어") || nameLower.contains("제약") -> "BIO"
            nameLower.contains("자동차") || nameLower.contains("모빌리티") -> "AUTO"
            nameLower.contains("금융") || nameLower.contains("은행") || nameLower.contains("보험") -> "FINANCE"
            nameLower.contains("it") || nameLower.contains("소프트웨어") || nameLower.contains("게임") || nameLower.contains("인터넷") -> "IT"
            nameLower.contains("화학") || nameLower.contains("정유") -> "CHEMICAL"
            nameLower.contains("철강") || nameLower.contains("금속") || nameLower.contains("비철") -> "STEEL"
            nameLower.contains("건설") || nameLower.contains("인프라") -> "CONSTRUCTION"
            nameLower.contains("소비재") || nameLower.contains("유통") || nameLower.contains("리테일") -> "RETAIL"
            nameLower.contains("엔터") || nameLower.contains("미디어") || nameLower.contains("콘텐츠") -> "ENTERTAINMENT"
            nameLower.contains("에너지") || nameLower.contains("신재생") || nameLower.contains("유틸리티") -> "ENERGY"
            nameLower.contains("통신") || nameLower.contains("5g") -> "TELECOM"
            nameLower.contains("조선") || nameLower.contains("해운") -> "SHIPBUILDING"
            nameLower.contains("방산") || nameLower.contains("항공") -> "DEFENSE"
            else -> "OTHER"
        }
    }

    /**
     * 종목 티커와 이름에서 섹터 추론
     *
     * @param ticker 종목 코드 (6자리)
     * @param name 종목명
     * @return 섹터 코드
     */
    fun inferSectorFromStock(ticker: String, name: String): String {
        // 1. 티커 기반 직접 매핑 (가장 정확)
        TICKER_TO_SECTOR[ticker]?.let { return it }

        // 2. 종목명 키워드 패턴 매칭
        val nameLower = name.lowercase()
        for ((patterns, sector) in NAME_PATTERNS) {
            if (patterns.any { nameLower.contains(it.lowercase()) }) {
                return sector
            }
        }

        // 3. 기본값
        return "OTHER"
    }

    /**
     * 섹터 코드를 한글 이름으로 변환
     */
    fun getSectorDisplayName(sector: String): String {
        return SECTOR_NAMES[sector] ?: "기타"
    }

    /**
     * 모든 섹터 코드 목록 조회
     */
    fun getAllSectorCodes(): List<String> = SECTOR_NAMES.keys.toList()
}

/**
 * 섹터 로테이션 신호
 */
data class SectorRotationSignal(
    val fromSector: String,
    val toSector: String,
    val confidence: Double,
    val flowDifference: Double,  // 자금 흐름 차이
    val description: String
)
