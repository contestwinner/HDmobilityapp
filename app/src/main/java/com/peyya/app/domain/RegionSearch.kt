package com.peyya.app.domain

/** Finds courses by a real place name: region label, course title, stop names or addresses. */
object RegionSearch {
    /** Official long names → the short form used in addresses ("경북 경주시 …"). */
    private val aliases = mapOf(
        "서울특별시" to "서울", "부산광역시" to "부산", "대구광역시" to "대구", "인천광역시" to "인천",
        "광주광역시" to "광주", "대전광역시" to "대전", "울산광역시" to "울산",
        "경기도" to "경기", "강원특별자치도" to "강원", "강원도" to "강원",
        "충청북도" to "충북", "충청남도" to "충남", "전북특별자치도" to "전북", "전라북도" to "전북",
        "전라남도" to "전남", "경상북도" to "경북", "경상남도" to "경남",
        "제주특별자치도" to "제주", "제주도" to "제주",
    )
    private val adminSuffixes = listOf("시", "군", "구")

    fun filter(routes: List<CommunityRoute>, query: String): List<CommunityRoute> {
        val q = normalizeQuery(query)
        if (q.isEmpty()) return routes
        return routes.filter { route -> haystack(route).any { it.contains(q) } }
    }

    internal fun normalizeQuery(query: String): String {
        val compact = query.replace(" ", "")
        aliases[compact]?.let { return it }
        val trimmed = adminSuffixes.firstOrNull { compact.length > 2 && compact.endsWith(it) }
            ?.let { compact.dropLast(it.length) }
        return (trimmed ?: compact).lowercase()
    }

    private fun haystack(route: CommunityRoute): List<String> =
        (listOf(route.region, route.title) + route.stops.flatMap { listOf(it.name, it.address) })
            .map { it.replace(" ", "").lowercase() }
}
