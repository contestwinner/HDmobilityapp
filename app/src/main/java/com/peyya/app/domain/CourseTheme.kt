package com.peyya.app.domain

/** What the family wants out of the day; drives the plan tab's theme recommendations. */
enum class CourseTheme(val labelKo: String) {
    Healing("힐링"),
    Experience("체험"),
    Food("맛집"),
    Kids("아이와 함께"),
    Culture("문화·역사"),
}

object ThemeCourses {
    /** Courses tagged with [theme], most liked first. */
    fun of(routes: List<CommunityRoute>, theme: CourseTheme): List<CommunityRoute> =
        routes.filter { theme in it.themes }.sortedByDescending { it.likes }
}
