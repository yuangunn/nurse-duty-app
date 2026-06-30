package com.nurseduty.domain

import java.time.LocalDate

/**
 * Timezone-independent calendar-day key (yyyymmdd). We use [LocalDate] (no timezone) so a day
 * never shifts or duplicates across timezone/DST changes — the same fix as iOS's DayKey.
 */
object DayKey {
    fun from(date: LocalDate): Int = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth

    fun toLocalDate(key: Int): LocalDate = LocalDate.of(key / 10000, (key / 100) % 100, key % 100)
}
