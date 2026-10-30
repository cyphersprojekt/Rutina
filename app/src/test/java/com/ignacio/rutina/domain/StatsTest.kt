package com.ignacio.rutina.domain

import com.ignacio.rutina.data.CounterGoal
import com.ignacio.rutina.data.Entry
import com.ignacio.rutina.data.Task
import com.ignacio.rutina.data.TaskKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class StatsTest {
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")

    private fun reminder(createdAt: LocalDateTime, vararg times: String) = Task(
        id = 1,
        name = "Cocinar",
        kind = TaskKind.REMINDER,
        times = times.map(LocalTime::parse),
        createdAt = createdAt.toEpochMillis(zone),
    )

    private fun done(date: LocalDate, slot: String, at: LocalDateTime) =
        Entry(taskId = 1, slotDate = date, slotTime = LocalTime.parse(slot), doneAt = at.toEpochMillis(zone))

    @Test
    fun `detecta que una tarea de las 17 se hace siempre a las 18`() {
        val task = reminder(LocalDateTime.of(2026, 10, 1, 0, 0), "17:00")
        val days = (1..9).map { LocalDate.of(2026, 10, it) }
        val entries = days
            .filter { it.dayOfMonth != 5 } // el 5 no se hizo
            .map { day -> done(day, "17:00", if (day.dayOfMonth == 3) day.atTime(17, 5) else day.atTime(18, 0)) }

        val stats = reminderStats(task, entries, days.first(), LocalDateTime.of(2026, 10, 10, 12, 0), zone)

        assertEquals(9, stats.total)
        assertEquals(8, stats.done)
        assertEquals(1, stats.onTime)
        assertEquals(60L, stats.medianDelay)
        assertEquals(8f / 9f, stats.adherence!!, 0.0001f)
        assertNull(stats.outcomes.single { it.date.dayOfMonth == 5 }.doneAt)
    }

    @Test
    fun `lo de hoy cuenta solo si ya se hizo`() {
        val task = reminder(LocalDateTime.of(2026, 10, 1, 0, 0), "09:00", "17:00")
        val today = LocalDate.of(2026, 10, 10)
        val now = today.atTime(18, 0)
        val entries = listOf(done(today, "17:00", today.atTime(17, 10)))

        val stats = reminderStats(task, entries, today, now, zone)

        // Las 09:00 de hoy siguen pendientes: no son un incumplimiento todavía.
        assertEquals(1, stats.total)
        assertEquals(1, stats.done)
        assertEquals(10L, stats.medianDelay)
    }

    @Test
    fun `no cuenta como incumplido lo anterior a la creacion de la tarea`() {
        val task = reminder(LocalDateTime.of(2026, 10, 5, 15, 0), "09:00", "20:00")

        val stats = reminderStats(task, emptyList(), LocalDate.of(2026, 10, 1), LocalDateTime.of(2026, 10, 7, 10, 0), zone)

        // 05/10 20:00, 06/10 09:00 y 06/10 20:00. Las 09:00 del 05/10 son anteriores a la creación.
        assertEquals(3, stats.total)
        assertEquals(0, stats.done)
        assertEquals(0f, stats.adherence!!, 0f)
    }

    @Test
    fun `separa las estadisticas por horario`() {
        val task = reminder(LocalDateTime.of(2026, 10, 1, 0, 0), "09:00", "21:00")
        val day = LocalDate.of(2026, 10, 2)
        val entries = listOf(
            done(day, "09:00", day.atTime(9, 5)),
            done(day, "21:00", day.atTime(22, 30)),
        )

        val stats = reminderStats(task, entries, day, LocalDateTime.of(2026, 10, 3, 8, 0), zone)

        assertEquals(listOf(5L, 90L), stats.bySlot.map { it.medianDelay })
    }

    @Test
    fun `el contador suma por dia y promedia desde el primer registro`() {
        val pushUps = Task(
            id = 2,
            name = "Flexiones",
            kind = TaskKind.COUNTER,
            // Se creó el 6 pero se empezó a usar el 8.
            createdAt = LocalDateTime.of(2026, 10, 6, 0, 0).toEpochMillis(zone),
        )
        fun add(day: Int, hour: Int, amount: Int) =
            Entry(taskId = 2, doneAt = LocalDateTime.of(2026, 10, day, hour, 0).toEpochMillis(zone), amount = amount)
        val entries = listOf(add(8, 10, 10), add(8, 18, 15), add(10, 9, 20))

        val stats = counterStats(pushUps, entries, LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 10), zone)

        assertEquals(7, stats.daily.size)
        assertEquals(45, stats.total)
        assertEquals(20, stats.today)
        assertEquals(15f, stats.dailyAverage, 0.0001f) // 45 en 3 días (8, 9 y 10)
        assertEquals(LocalDate.of(2026, 10, 8) to 25, stats.best)
    }

    @Test
    fun `por hora suma las cantidades de todos los dias del periodo`() {
        val coffee = Task(id = 4, name = "Café", kind = TaskKind.COUNTER, createdAt = 0)
        fun add(day: Int, hour: Int, minute: Int, amount: Int) =
            Entry(taskId = 4, doneAt = LocalDateTime.of(2026, 10, day, hour, minute).toEpochMillis(zone), amount = amount)
        val entries = listOf(
            // Antes del período: no cuenta.
            add(1, 10, 0, 50),
            // Ocho de una vez, anotados de a poco.
            add(8, 10, 5, 5), add(8, 10, 20, 1), add(8, 10, 40, 1), add(8, 10, 59, 1),
            add(9, 10, 30, 2),
            add(9, 15, 0, 1),
            add(10, 23, 59, 3),
        )

        val stats = counterStats(coffee, entries, LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 10), zone)

        assertEquals(24, stats.hourly.size)
        assertEquals(10, stats.hourly[10])
        assertEquals(1, stats.hourly[15])
        assertEquals(3, stats.hourly[23])
        assertEquals(14, stats.hourly.sum())
        assertEquals(10, stats.peakHour)
    }

    @Test
    fun `sin registros en el periodo no hay hora de mas`() {
        val coffee = Task(id = 4, name = "Café", kind = TaskKind.COUNTER, createdAt = 0)
        val stats = counterStats(coffee, emptyList(), LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 10), zone)

        assertEquals(List(24) { 0 }, stats.hourly)
        assertNull(stats.peakHour)
    }

    private fun days(vararg totals: Int): List<Pair<LocalDate, Int>> =
        totals.mapIndexed { index, total -> LocalDate.of(2026, 10, 1).plusDays(index.toLong()) to total }

    @Test
    fun `detecta que se fuma cada vez menos`() {
        val trend = trend(days(15, 16, 14, 14, 13, 12, 13, 11, 10, 10))!!

        assertEquals(TrendDirection.DOWN, trend.direction)
        assertTrue(trend.start > trend.end)
        assertEquals(true, trend.isGood(CounterGoal.LESS))
        assertEquals(false, trend.isGood(CounterGoal.MORE))
    }

    @Test
    fun `detecta que se hace cada vez mas`() {
        val trend = trend(days(20, 25, 20, 30, 35, 30, 40))!!

        assertEquals(TrendDirection.UP, trend.direction)
        assertEquals(true, trend.isGood(CounterGoal.MORE))
    }

    @Test
    fun `los dias que suben y bajan sin rumbo no son una tendencia`() {
        val trend = trend(days(10, 12, 9, 11, 10, 12, 9, 11, 10, 11))!!

        assertEquals(TrendDirection.FLAT, trend.direction)
        assertNull(trend.isGood(CounterGoal.LESS))
    }

    @Test
    fun `un solo dia distinto no alcanza para marcar una tendencia`() {
        // Cinco días de flexiones y un sábado sin hacer: no es que se esté abandonando.
        assertEquals(TrendDirection.FLAT, trend(days(50, 50, 50, 50, 50, 0))!!.direction)
    }

    @Test
    fun `sin datos o con pocos dias no hay tendencia`() {
        assertNull(trend(days(3, 2, 1, 0)))
        assertEquals(TrendDirection.FLAT, trend(days(0, 0, 0, 0, 0))!!.direction)
    }

    @Test
    fun `si conviene hacer menos el mejor dia es el de menos, sin contar hoy`() {
        val cigarettes = Task(
            id = 3,
            name = "Cigarrillos",
            kind = TaskKind.COUNTER,
            goal = CounterGoal.LESS,
            createdAt = LocalDateTime.of(2026, 9, 28, 0, 0).toEpochMillis(zone),
        )
        val perDay = mapOf(3 to 12, 4 to 10, 5 to 9, 6 to 9, 7 to 8, 8 to 7, 9 to 6, 10 to 2)
        val entries = perDay.map { (day, amount) ->
            Entry(taskId = 3, doneAt = LocalDateTime.of(2026, 10, day, 12, 0).toEpochMillis(zone), amount = amount)
        }

        val stats = counterStats(cigarettes, entries, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10), zone)

        // El 10 es hoy y recién va 2; el 1 y el 2 no se anotó nada porque todavía no se usaba el contador.
        assertEquals(LocalDate.of(2026, 10, 9) to 6, stats.best)
        val trend = stats.trend!!
        assertEquals(LocalDate.of(2026, 10, 3), trend.firstDay)
        assertEquals(LocalDate.of(2026, 10, 9), trend.lastDay)
        assertEquals(TrendDirection.DOWN, trend.direction)
    }
}
