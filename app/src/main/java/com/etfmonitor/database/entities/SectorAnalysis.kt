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
 * KRX GICS(Global Industry Classification Standard) 기반 업종 분류 체계 적용
 * - 11개 GICS 섹터 + 한국 시장 특화 세부 섹터
 * - KRX 업종 코드 참조: https://data.krx.co.kr
 *
 * 분류 우선순위:
 * 1. KRX 업종 코드 기반 (종목 티커로 조회)
 * 2. 종목명 키워드 기반 (패턴 매칭)
 * 3. 기본값: OTHER
 */
object SectorMapping {

    /**
     * KRX GICS 기반 섹터 분류
     * 섹터코드 형식: G{GICS섹터번호}_{세부분류}
     */
    val SECTOR_NAMES = mapOf(
        // GICS 10: 에너지
        "G10_ENERGY" to "에너지",

        // GICS 15: 소재
        "G15_MATERIALS" to "소재",
        "G15_CHEMICAL" to "화학",
        "G15_STEEL" to "철강/금속",

        // GICS 20: 산업재
        "G20_INDUSTRIALS" to "산업재",
        "G20_CONSTRUCTION" to "건설",
        "G20_SHIPBUILDING" to "조선/기계",
        "G20_DEFENSE" to "방산/항공",
        "G20_TRANSPORTATION" to "운송",

        // GICS 25: 경기소비재(자유소비재)
        "G25_CONSUMER_DISC" to "경기소비재",
        "G25_AUTO" to "자동차",
        "G25_RETAIL" to "유통",
        "G25_ENTERTAINMENT" to "미디어/엔터",

        // GICS 30: 필수소비재
        "G30_CONSUMER_STAPLES" to "필수소비재",
        "G30_FOOD" to "음식료",
        "G30_COSMETICS" to "화장품/생활",

        // GICS 35: 헬스케어
        "G35_HEALTHCARE" to "헬스케어",
        "G35_PHARMA" to "제약",
        "G35_BIO" to "바이오",
        "G35_MEDICAL" to "의료기기",

        // GICS 40: 금융
        "G40_FINANCIALS" to "금융",
        "G40_BANK" to "은행",
        "G40_SECURITIES" to "증권",
        "G40_INSURANCE" to "보험",

        // GICS 45: IT
        "G45_IT" to "IT",
        "G45_SEMICONDUCTOR" to "반도체",
        "G45_SOFTWARE" to "소프트웨어",
        "G45_HARDWARE" to "하드웨어",
        "G45_BATTERY" to "2차전지",

        // GICS 50: 커뮤니케이션서비스
        "G50_COMM_SERVICES" to "커뮤니케이션",
        "G50_TELECOM" to "통신",
        "G50_MEDIA" to "미디어",
        "G50_INTERNET" to "인터넷",

        // GICS 55: 유틸리티
        "G55_UTILITIES" to "유틸리티",

        // GICS 60: 부동산
        "G60_REAL_ESTATE" to "부동산",

        // 기타
        "OTHER" to "기타"
    )

    /**
     * 레거시 섹터 코드 → GICS 섹터 코드 매핑 (하위 호환성)
     */
    private val LEGACY_TO_GICS = mapOf(
        "SEMICONDUCTOR" to "G45_SEMICONDUCTOR",
        "BATTERY" to "G45_BATTERY",
        "BIO" to "G35_BIO",
        "AUTO" to "G25_AUTO",
        "FINANCE" to "G40_FINANCIALS",
        "IT" to "G45_IT",
        "CHEMICAL" to "G15_CHEMICAL",
        "STEEL" to "G15_STEEL",
        "CONSTRUCTION" to "G20_CONSTRUCTION",
        "RETAIL" to "G25_RETAIL",
        "ENTERTAINMENT" to "G25_ENTERTAINMENT",
        "ENERGY" to "G10_ENERGY",
        "TELECOM" to "G50_TELECOM",
        "SHIPBUILDING" to "G20_SHIPBUILDING",
        "DEFENSE" to "G20_DEFENSE"
    )

    /**
     * KRX 종목 티커별 GICS 섹터 매핑 (KOSPI/KOSDAQ 대형주)
     * 한국거래소 업종 분류 기준 + GICS 섹터 코드 적용
     */
    private val TICKER_TO_SECTOR = mapOf(
        // G45_SEMICONDUCTOR: 반도체
        "005930" to "G45_SEMICONDUCTOR",  // 삼성전자
        "000660" to "G45_SEMICONDUCTOR",  // SK하이닉스
        "042700" to "G45_SEMICONDUCTOR",  // 한미반도체
        "403870" to "G45_SEMICONDUCTOR",  // HPSP
        "058470" to "G45_SEMICONDUCTOR",  // 리노공업
        "357780" to "G45_SEMICONDUCTOR",  // 솔브레인
        "036830" to "G45_SEMICONDUCTOR",  // 솔브레인홀딩스
        "086390" to "G45_SEMICONDUCTOR",  // 유니테스트
        "095340" to "G45_SEMICONDUCTOR",  // ISC
        "402340" to "G45_SEMICONDUCTOR",  // SK스퀘어
        "000990" to "G45_SEMICONDUCTOR",  // DB하이텍
        "166090" to "G45_SEMICONDUCTOR",  // 하나머티리얼즈

        // G45_BATTERY: 2차전지
        "373220" to "G45_BATTERY",  // LG에너지솔루션
        "006400" to "G45_BATTERY",  // 삼성SDI
        "247540" to "G45_BATTERY",  // 에코프로비엠
        "086520" to "G45_BATTERY",  // 에코프로
        "003670" to "G45_BATTERY",  // 포스코퓨처엠
        "298040" to "G45_BATTERY",  // 효성중공업

        // G35_BIO: 바이오
        "068270" to "G35_BIO",  // 셀트리온
        "207940" to "G35_BIO",  // 삼성바이오로직스
        "326030" to "G35_BIO",  // SK바이오팜
        "091990" to "G35_BIO",  // 셀트리온헬스케어
        "302440" to "G35_BIO",  // SK바이오사이언스
        "145020" to "G35_BIO",  // 휴젤
        "328130" to "G35_BIO",  // 루닛
        "141080" to "G35_BIO",  // 레고켐바이오
        "196170" to "G35_BIO",  // 알테오젠

        // G35_PHARMA: 제약
        "128940" to "G35_PHARMA",  // 한미약품
        "000100" to "G35_PHARMA",  // 유한양행
        "185750" to "G35_PHARMA",  // 종근당
        "006280" to "G35_PHARMA",  // 녹십자

        // G25_AUTO: 자동차
        "005380" to "G25_AUTO",  // 현대차
        "000270" to "G25_AUTO",  // 기아
        "005387" to "G25_AUTO",  // 현대차2우B
        "005385" to "G25_AUTO",  // 현대차우
        "012330" to "G25_AUTO",  // 현대모비스
        "011210" to "G25_AUTO",  // 현대위아
        "004490" to "G25_AUTO",  // 세방전지
        "161390" to "G25_AUTO",  // 한국타이어앤테크놀로지
        "018880" to "G25_AUTO",  // 한온시스템

        // G40_BANK: 은행
        "105560" to "G40_BANK",  // KB금융
        "055550" to "G40_BANK",  // 신한지주
        "086790" to "G40_BANK",  // 하나금융지주
        "316140" to "G40_BANK",  // 우리금융지주
        "024110" to "G40_BANK",  // 기업은행
        "138930" to "G40_BANK",  // BNK금융지주
        "139130" to "G40_BANK",  // DGB금융지주

        // G40_INSURANCE: 보험
        "000810" to "G40_INSURANCE",  // 삼성화재
        "032830" to "G40_INSURANCE",  // 삼성생명

        // G40_SECURITIES: 증권
        "071050" to "G40_SECURITIES",  // 한국금융지주
        "005940" to "G40_SECURITIES",  // NH투자증권
        "006800" to "G40_SECURITIES",  // 미래에셋증권
        "016360" to "G40_SECURITIES",  // 삼성증권

        // G50_INTERNET: 인터넷/플랫폼
        "035720" to "G50_INTERNET",  // 카카오
        "035420" to "G50_INTERNET",  // NAVER

        // G45_SOFTWARE: 소프트웨어/게임
        "259960" to "G45_SOFTWARE",  // 크래프톤
        "263750" to "G45_SOFTWARE",  // 펄어비스
        "251270" to "G45_SOFTWARE",  // 넷마블
        "293490" to "G45_SOFTWARE",  // 카카오게임즈
        "036570" to "G45_SOFTWARE",  // 엔씨소프트
        "030520" to "G45_SOFTWARE",  // 한글과컴퓨터

        // G25_ENTERTAINMENT: 엔터테인먼트
        "041510" to "G25_ENTERTAINMENT",  // 에스엠
        "352820" to "G25_ENTERTAINMENT",  // 하이브
        "122870" to "G25_ENTERTAINMENT",  // YG엔터테인먼트
        "035900" to "G25_ENTERTAINMENT",  // JYP Ent.
        "079160" to "G25_ENTERTAINMENT",  // CJ CGV

        // G15_CHEMICAL: 화학
        "051910" to "G15_CHEMICAL",  // LG화학
        "011170" to "G15_CHEMICAL",  // 롯데케미칼
        "006650" to "G15_CHEMICAL",  // 대한유화
        "004000" to "G15_CHEMICAL",  // 롯데정밀화학
        "010060" to "G15_CHEMICAL",  // OCI홀딩스

        // G10_ENERGY: 에너지
        "010950" to "G10_ENERGY",  // S-Oil
        "096770" to "G10_ENERGY",  // SK이노베이션
        "078930" to "G10_ENERGY",  // GS

        // G15_STEEL: 철강/금속
        "005490" to "G15_STEEL",  // POSCO홀딩스
        "004020" to "G15_STEEL",  // 현대제철
        "010130" to "G15_STEEL",  // 고려아연
        "103140" to "G15_STEEL",  // 풍산
        "001230" to "G15_STEEL",  // 동국제강

        // G20_CONSTRUCTION: 건설
        "000720" to "G20_CONSTRUCTION",  // 현대건설
        "047040" to "G20_CONSTRUCTION",  // 대우건설
        "006360" to "G20_CONSTRUCTION",  // GS건설
        "028050" to "G20_CONSTRUCTION",  // 삼성엔지니어링
        "000210" to "G20_CONSTRUCTION",  // DL
        "375500" to "G20_CONSTRUCTION",  // DL이앤씨

        // G25_RETAIL: 유통
        "004170" to "G25_RETAIL",  // 신세계
        "069960" to "G25_RETAIL",  // 현대백화점
        "007070" to "G25_RETAIL",  // GS리테일
        "004990" to "G25_RETAIL",  // 롯데지주
        "023530" to "G25_RETAIL",  // 롯데쇼핑
        "139480" to "G25_RETAIL",  // 이마트

        // G30_COSMETICS: 화장품/생활
        "090430" to "G30_COSMETICS",  // 아모레퍼시픽
        "002790" to "G30_COSMETICS",  // 아모레G
        "021240" to "G30_COSMETICS",  // 코웨이

        // G30_FOOD: 음식료
        "097950" to "G30_FOOD",  // CJ제일제당
        "001040" to "G30_FOOD",  // CJ

        // G55_UTILITIES: 유틸리티
        "015760" to "G55_UTILITIES",  // 한국전력
        "036460" to "G55_UTILITIES",  // 한국가스공사
        "034020" to "G55_UTILITIES",  // 두산에너빌리티

        // G50_TELECOM: 통신
        "030200" to "G50_TELECOM",  // KT
        "017670" to "G50_TELECOM",  // SK텔레콤
        "032640" to "G50_TELECOM",  // LG유플러스

        // G20_SHIPBUILDING: 조선/기계
        "009540" to "G20_SHIPBUILDING",  // 한국조선해양
        "329180" to "G20_SHIPBUILDING",  // HD현대중공업
        "042660" to "G20_SHIPBUILDING",  // 한화오션
        "010620" to "G20_SHIPBUILDING",  // 현대미포조선
        "267250" to "G20_SHIPBUILDING",  // HD현대

        // G20_TRANSPORTATION: 운송
        "011200" to "G20_TRANSPORTATION",  // HMM
        "028670" to "G20_TRANSPORTATION",  // 팬오션
        "003490" to "G20_TRANSPORTATION",  // 대한항공
        "089590" to "G20_TRANSPORTATION",  // 제주항공
        "180640" to "G20_TRANSPORTATION",  // 한진칼

        // G20_DEFENSE: 방산/항공
        "012450" to "G20_DEFENSE",  // 한화에어로스페이스
        "047810" to "G20_DEFENSE",  // 한국항공우주
        "012630" to "G20_DEFENSE",  // 현대로템

        // G45_HARDWARE: 전자부품/하드웨어
        "011070" to "G45_HARDWARE",  // LG이노텍
        "034730" to "G20_INDUSTRIALS",  // SK (지주)
        "003550" to "G20_INDUSTRIALS"   // LG (지주)
    )

    /**
     * 종목명 키워드별 GICS 섹터 매핑 (패턴 매칭용)
     */
    private val NAME_PATTERNS = listOf(
        // G45: IT
        listOf("반도체", "하이닉스", "마이크론", "메모리", "파운드리", "팹리스", "웨이퍼", "ASML") to "G45_SEMICONDUCTOR",
        listOf("배터리", "2차전지", "이차전지", "리튬", "양극재", "음극재", "전해질", "분리막", "에너지솔루션") to "G45_BATTERY",
        listOf("소프트웨어", "게임", "데이터", "클라우드", "AI", "인공지능", "SaaS") to "G45_SOFTWARE",
        listOf("전자부품", "디스플레이", "PCB", "OLED", "LCD") to "G45_HARDWARE",

        // G35: 헬스케어
        listOf("바이오", "신약", "임상", "세포", "유전자", "항체", "백신") to "G35_BIO",
        listOf("제약", "약품", "의약", "헬스케어") to "G35_PHARMA",
        listOf("의료기기", "진단", "의료장비", "병원") to "G35_MEDICAL",

        // G25: 경기소비재
        listOf("자동차", "모빌리티", "현대차", "기아", "모비스", "타이어", "전기차", "EV") to "G25_AUTO",
        listOf("유통", "마트", "백화점", "쇼핑", "리테일", "이커머스") to "G25_RETAIL",
        listOf("엔터", "미디어", "방송", "콘텐츠", "영화", "음악", "기획사", "드라마") to "G25_ENTERTAINMENT",

        // G30: 필수소비재
        listOf("식품", "음료", "음식료", "푸드") to "G30_FOOD",
        listOf("화장품", "뷰티", "생활용품", "세제") to "G30_COSMETICS",

        // G40: 금융
        listOf("은행", "금융지주", "저축은행") to "G40_BANK",
        listOf("증권", "투자", "자산운용") to "G40_SECURITIES",
        listOf("보험", "생명", "화재", "손해") to "G40_INSURANCE",

        // G50: 커뮤니케이션서비스
        listOf("통신", "텔레콤", "5G", "네트워크", "모바일") to "G50_TELECOM",
        listOf("카카오", "네이버", "인터넷", "플랫폼", "포털") to "G50_INTERNET",

        // G15: 소재
        listOf("화학", "정유", "석유", "화공", "페인트", "수지", "플라스틱", "고무") to "G15_CHEMICAL",
        listOf("철강", "금속", "포스코", "제철", "알루미늄", "동", "아연", "니켈") to "G15_STEEL",

        // G20: 산업재
        listOf("건설", "건축", "시공", "인프라", "토목", "플랜트", "엔지니어링") to "G20_CONSTRUCTION",
        listOf("조선", "해운", "선박", "해양", "중공업", "기계") to "G20_SHIPBUILDING",
        listOf("방산", "방위", "항공우주", "미사일", "무기") to "G20_DEFENSE",
        listOf("운송", "물류", "항공", "해운", "철도") to "G20_TRANSPORTATION",

        // G10: 에너지
        listOf("에너지", "정유", "석유", "가스", "신재생", "태양광", "풍력", "수소") to "G10_ENERGY",

        // G55: 유틸리티
        listOf("전력", "발전", "유틸리티", "가스공사", "전기") to "G55_UTILITIES",

        // G60: 부동산
        listOf("부동산", "리츠", "REITs", "임대", "오피스") to "G60_REAL_ESTATE"
    )

    /**
     * ETF 이름에서 GICS 섹터 추론
     */
    fun inferSectorFromEtfName(etfName: String): String {
        val nameLower = etfName.lowercase()
        return when {
            // G45: IT
            nameLower.contains("반도체") || nameLower.contains("시스템반도체") -> "G45_SEMICONDUCTOR"
            nameLower.contains("2차전지") || nameLower.contains("배터리") -> "G45_BATTERY"
            nameLower.contains("소프트웨어") || nameLower.contains("게임") -> "G45_SOFTWARE"

            // G35: 헬스케어
            nameLower.contains("바이오") -> "G35_BIO"
            nameLower.contains("헬스케어") || nameLower.contains("의료") -> "G35_HEALTHCARE"
            nameLower.contains("제약") -> "G35_PHARMA"

            // G25: 경기소비재
            nameLower.contains("자동차") || nameLower.contains("모빌리티") || nameLower.contains("전기차") -> "G25_AUTO"
            nameLower.contains("유통") || nameLower.contains("리테일") -> "G25_RETAIL"
            nameLower.contains("엔터") || nameLower.contains("미디어") || nameLower.contains("콘텐츠") -> "G25_ENTERTAINMENT"

            // G30: 필수소비재
            nameLower.contains("소비재") || nameLower.contains("식품") || nameLower.contains("음식료") -> "G30_CONSUMER_STAPLES"
            nameLower.contains("화장품") -> "G30_COSMETICS"

            // G40: 금융
            nameLower.contains("금융") -> "G40_FINANCIALS"
            nameLower.contains("은행") -> "G40_BANK"
            nameLower.contains("보험") -> "G40_INSURANCE"

            // G50: 커뮤니케이션
            nameLower.contains("통신") || nameLower.contains("5g") -> "G50_TELECOM"
            nameLower.contains("인터넷") || nameLower.contains("it") -> "G50_INTERNET"

            // G15: 소재
            nameLower.contains("화학") || nameLower.contains("정유") -> "G15_CHEMICAL"
            nameLower.contains("철강") || nameLower.contains("금속") || nameLower.contains("비철") -> "G15_STEEL"

            // G20: 산업재
            nameLower.contains("건설") || nameLower.contains("인프라") -> "G20_CONSTRUCTION"
            nameLower.contains("조선") || nameLower.contains("기계") -> "G20_SHIPBUILDING"
            nameLower.contains("방산") || nameLower.contains("항공") -> "G20_DEFENSE"
            nameLower.contains("운송") || nameLower.contains("물류") || nameLower.contains("해운") -> "G20_TRANSPORTATION"

            // G10: 에너지
            nameLower.contains("에너지") || nameLower.contains("신재생") -> "G10_ENERGY"

            // G55: 유틸리티
            nameLower.contains("유틸리티") || nameLower.contains("전력") -> "G55_UTILITIES"

            // G60: 부동산
            nameLower.contains("부동산") || nameLower.contains("리츠") -> "G60_REAL_ESTATE"

            else -> "OTHER"
        }
    }

    /**
     * 레거시 섹터 코드를 GICS 코드로 변환
     */
    fun convertLegacyToGics(legacyCode: String): String {
        return LEGACY_TO_GICS[legacyCode] ?: legacyCode
    }

    /**
     * GICS 코드에서 상위 섹터(2자리) 추출
     * 예: G45_SEMICONDUCTOR → G45
     */
    fun getGicsSector(code: String): String {
        return if (code.startsWith("G") && code.length >= 3) {
            code.substring(0, 3)
        } else {
            "OTHER"
        }
    }

    /**
     * GICS 상위 섹터명 조회
     */
    fun getGicsSectorName(gicsCode: String): String {
        return when (gicsCode) {
            "G10" -> "에너지"
            "G15" -> "소재"
            "G20" -> "산업재"
            "G25" -> "경기소비재"
            "G30" -> "필수소비재"
            "G35" -> "헬스케어"
            "G40" -> "금융"
            "G45" -> "IT"
            "G50" -> "커뮤니케이션"
            "G55" -> "유틸리티"
            "G60" -> "부동산"
            else -> "기타"
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
     * 섹터 코드를 한글 이름으로 변환 (GICS 및 레거시 코드 모두 지원)
     */
    fun getSectorDisplayName(sector: String): String {
        // GICS 코드로 직접 조회
        SECTOR_NAMES[sector]?.let { return it }

        // 레거시 코드를 GICS로 변환 후 조회
        LEGACY_TO_GICS[sector]?.let { gicsCode ->
            SECTOR_NAMES[gicsCode]?.let { return it }
        }

        return "기타"
    }

    /**
     * 모든 섹터 코드 목록 조회 (GICS 코드만)
     */
    fun getAllSectorCodes(): List<String> = SECTOR_NAMES.keys.toList()

    /**
     * GICS 상위 섹터별로 그룹화된 세부 섹터 조회
     */
    fun getSectorsByGicsGroup(): Map<String, List<String>> {
        return SECTOR_NAMES.keys
            .filter { it.startsWith("G") }
            .groupBy { getGicsSector(it) }
    }
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
