package com.peyya.app.data

import com.peyya.app.domain.CommunityComment
import com.peyya.app.domain.CommunityRoute
import com.peyya.app.domain.CourseTheme
import com.peyya.app.domain.Stop
import com.peyya.app.domain.StopCategory

/**
 * Courses outside Seoul so region search and theme picks cover the whole country.
 * Places are real public landmarks; authors, likes and comments are demo content.
 */
object NationwideCatalog {
    private fun stop(id: String, name: String, address: String, lat: Double, lon: Double) =
        Stop(id = id, name = name, address = address, latitude = lat, longitude = lon, poiId = "peyya-demo-$id")

    val gyeongpoBeach = stop("gyeongpo-beach", "경포해변", "강원 강릉시 창해로 514", 37.8055, 128.9087)
    val anmokCafe = stop("anmok-cafe", "안목해변 커피거리", "강원 강릉시 창해로14번길 일대", 37.7720, 128.9476)
    val haeundaeBeach = stop("haeundae-beach", "해운대해수욕장", "부산 해운대구 해운대해변로 264", 35.1587, 129.1604)
    val gwangalliBeach = stop("gwangalli-beach", "광안리해수욕장", "부산 수영구 광안해변로 219", 35.1532, 129.1187)
    val bulguksa = stop("bulguksa", "불국사", "경북 경주시 불국로 385", 35.7900, 129.3320)
    val hwangridan = stop("hwangridan-cafe", "황리단길", "경북 경주시 포석로 1080 일대", 35.8380, 129.2110)
    val jeonjuHanok = stop("jeonju-hanok", "전주한옥마을", "전북 전주시 완산구 기린대로 99", 35.8150, 127.1530)
    val nambuMarket = stop("nambu-market", "전주 남부시장", "전북 전주시 완산구 풍남문1길 19-3", 35.8128, 127.1470)
    val seongsanIlchulbong = stop("seongsan-sea", "성산일출봉", "제주 서귀포시 성산읍 일출로 284-12", 33.4580, 126.9425)
    val seopjikoji = stop("seopjikoji-sea", "섭지코지", "제주 서귀포시 성산읍 섭지코지로 262", 33.4240, 126.9300)
    val morningCalm = stop("morningcalm-garden", "아침고요수목원", "경기 가평군 상면 수목원로 432", 37.7430, 127.3520)
    val petiteFrance = stop("petitefrance", "쁘띠프랑스", "경기 가평군 청평면 호반로 1063", 37.7166, 127.4800)
    val scienceMuseum = stop("science-center", "국립중앙과학관", "대전 유성구 대덕대로 481", 36.3760, 127.3760)
    val sungsimdang = stop("sungsimdang-bakery", "성심당 본점", "대전 중구 대종로480번길 15", 36.3276, 127.4274)
    val yeosuCableCar = stop("yeosu-sea", "여수해상케이블카", "전남 여수시 돌산읍 돌산로 3600-1", 34.7360, 127.7460)
    val yisunsinSquare = stop("yisunsin-square", "이순신광장", "전남 여수시 중앙동 이순신광장", 34.7390, 127.7360)

    fun courses(now: Long): List<CommunityRoute> = listOf(
        course(
            "comm-gangneung", "강릉", "경포 바다 산책 + 안목 커피거리", listOf(gyeongpoBeach, anmokCafe),
            "바닷바람 맞고 커피 한 잔. 아이들은 모래놀이로 한 시간 순삭.", 142,
            listOf(CourseTheme.Healing, CourseTheme.Food), PeyyaImages.CAFE,
            comment("파도소리", "안목 쪽은 오전에 가야 주차가 편해요.", now - DAY * 3),
        ),
        course(
            "comm-busan", "부산", "해운대 산책 + 광안리 야경", listOf(haeundaeBeach, gwangalliBeach),
            "해 질 무렵 광안대교 불 켜지는 순간이 하이라이트.", 118,
            listOf(CourseTheme.Healing, CourseTheme.Experience), PeyyaImages.CITY,
        ),
        course(
            "comm-gyeongju", "경주", "불국사 + 황리단길 반나절", listOf(bulguksa, hwangridan),
            "교과서에서 본 걸 직접 보니 아이가 질문이 많아졌어요.", 97,
            listOf(CourseTheme.Culture, CourseTheme.Kids), PeyyaImages.PARK,
            comment("역사덕후아빠", "불국사 주차장은 10시 전 도착 추천.", now - DAY * 5),
        ),
        course(
            "comm-jeonju", "전주", "한옥마을 걷고 남부시장 먹방", listOf(jeonjuHanok, nambuMarket),
            "한복 대여 후 사진 찍고, 시장에서 비빔밥으로 마무리.", 88,
            listOf(CourseTheme.Food, CourseTheme.Culture), PeyyaImages.FOOD,
        ),
        course(
            "comm-jeju", "제주", "성산일출봉 + 섭지코지 해안길", listOf(seongsanIlchulbong, seopjikoji),
            "렌터카로 동쪽 해안 한 바퀴. 일출봉은 아이도 30분이면 올라가요.", 156,
            listOf(CourseTheme.Healing, CourseTheme.Experience), PeyyaImages.ONBOARD,
        ),
        course(
            "comm-gapyeong", "가평", "아침고요수목원 + 쁘띠프랑스", listOf(morningCalm, petiteFrance),
            "꽃길 산책 후 동화 마을. 서울에서 1시간 반이면 도착.", 73,
            listOf(CourseTheme.Healing, CourseTheme.Kids), PeyyaImages.PARK,
        ),
        course(
            "comm-daejeon", "대전", "국립중앙과학관 체험 + 성심당 빵", listOf(scienceMuseum, sungsimdang),
            "비 오는 날 실내 체험으로 딱. 빵은 줄이 길어도 금방 빠져요.", 104,
            listOf(CourseTheme.Experience, CourseTheme.Kids, CourseTheme.Food), PeyyaImages.BRUNCH,
            comment("빵순이엄마", "튀김소보로는 꼭 사세요.", now - DAY),
        ),
        course(
            "comm-yeosu", "여수", "해상케이블카 + 이순신광장", listOf(yeosuCableCar, yisunsinSquare),
            "크리스털 캐빈에서 바다 내려다보기, 광장에서 게장 한 상.", 81,
            listOf(CourseTheme.Experience, CourseTheme.Food), PeyyaImages.ONBOARD,
        ),
    )

    private fun course(
        id: String,
        region: String,
        title: String,
        stops: List<Stop>,
        note: String,
        likes: Int,
        themes: List<CourseTheme>,
        image: String,
        vararg comments: CommunityComment,
    ) = CommunityRoute(
        id = id,
        region = region,
        title = title,
        stops = stops,
        authorLabel = "$region 주말러",
        likes = likes,
        likedByMe = false,
        satisfactionNote = note,
        comments = comments.toList(),
        heroTone = StopCategory.Park,
        reviewImageModel = image,
        themes = themes,
    )

    private fun comment(author: String, body: String, at: Long) =
        CommunityComment(id = "c-${author.hashCode()}", authorLabel = author, body = body, createdAtEpochMs = at)

    private const val DAY = 86_400_000L
}
