package de.pixel.clashreminders.domain

import de.pixel.clashreminders.api.dto.CurrentWarDto
import de.pixel.clashreminders.api.dto.RaidSeasonsDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixture-based tests against realistic CoC API response shapes. */
class EventAnalysisTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val warJson = """
        {
          "state": "inWar",
          "teamSize": 15,
          "attacksPerMember": 2,
          "startTime": "20260611T070000.000Z",
          "endTime": "20260612T070000.000Z",
          "clan": {
            "tag": "#AAA",
            "name": "LOST",
            "members": [
              {"tag": "#P1", "name": "Alice", "mapPosition": 1,
               "attacks": [{"order": 1}, {"order": 2}]},
              {"tag": "#P2", "name": "Bob", "mapPosition": 2,
               "attacks": [{"order": 3}]},
              {"tag": "#P3", "name": "Carol", "mapPosition": 3}
            ]
          },
          "opponent": {"tag": "#BBB", "name": "Enemy", "members": []}
        }
    """.trimIndent()

    private val mainAccount = AccountRef("#P2", "Bob")
    private val secondAccount = AccountRef("#P3", "Carol")
    private val outsiderAccount = AccountRef("#P9", "NotInWar")

    @Test
    fun `openAccountAttacks lists only own accounts below required attacks`() {
        val war = json.decodeFromString<CurrentWarDto>(warJson)
        val side = WarAnalysis.ourSide(war, "#AAA")!!
        val open = WarAnalysis.openAccountAttacks(
            side,
            WarAnalysis.requiredAttacks(war),
            listOf(mainAccount, secondAccount, outsiderAccount),
        )
        // Alice (2/2) is not an own account; Bob (1/2) and Carol (0/2) are open
        assertEquals(listOf("Bob", "Carol"), open.map { it.name })
        assertEquals(listOf(1, 0), open.map { it.attacks })
        assertEquals(2, open.first().required)
    }

    @Test
    fun `openAccountAttacks empty when all own accounts are done`() {
        val war = json.decodeFromString<CurrentWarDto>(warJson)
        val side = WarAnalysis.ourSide(war, "#AAA")!!
        val open = WarAnalysis.openAccountAttacks(
            side,
            WarAnalysis.requiredAttacks(war),
            listOf(AccountRef("#P1", "Alice")),
        )
        assertTrue(open.isEmpty())
    }

    @Test
    fun `accountsInRoster ignores accounts outside the lineup`() {
        val war = json.decodeFromString<CurrentWarDto>(warJson)
        val side = WarAnalysis.ourSide(war, "#AAA")!!
        val inRoster = WarAnalysis.accountsInRoster(side, listOf(mainAccount, outsiderAccount))
        assertEquals(listOf("#P2"), inRoster.map { it.tag })
    }

    @Test
    fun `ourSide finds the clan on the opponent side too`() {
        val war = json.decodeFromString<CurrentWarDto>(warJson)
        assertEquals("Enemy", WarAnalysis.ourSide(war, "#BBB")?.name)
        assertNull(WarAnalysis.ourSide(war, "#CCC"))
    }

    @Test
    fun `cwl day requires one attack per account`() {
        val war = json.decodeFromString<CurrentWarDto>(warJson)
        val side = WarAnalysis.ourSide(war, "#AAA")!!
        val open = WarAnalysis.openAccountAttacks(
            side,
            WarAnalysis.CWL_ATTACKS_PER_DAY,
            listOf(mainAccount, secondAccount),
        )
        assertEquals(listOf("Carol"), open.map { it.name })
    }

    private val raidJson = """
        {
          "items": [
            {
              "state": "ongoing",
              "startTime": "20260612T070000.000Z",
              "endTime": "20260615T070000.000Z",
              "members": [
                {"tag": "#P1", "name": "Alice", "attacks": 6, "attackLimit": 5, "bonusAttackLimit": 1},
                {"tag": "#P2", "name": "Bob", "attacks": 2, "attackLimit": 5, "bonusAttackLimit": 0}
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `raid statuses count missing participants as zero attacks`() {
        val raid = json.decodeFromString<RaidSeasonsDto>(raidJson).items.first()
        val statuses = RaidAnalysis.accountStatuses(
            listOf(
                AccountRef("#P1", "Alice"),
                AccountRef("#P2", "Bob"),
                AccountRef("#P3", "Carol"),
            ),
            raid,
        )
        // Alice 6/6 done, Bob 2/5 open, Carol not joined -> 0/6 open
        assertEquals(listOf(false, true, true), statuses.map { it.open })
        assertEquals(0, statuses.first { it.name == "Carol" }.attacks)
        assertEquals(RaidAnalysis.MAX_ATTACKS_PER_MEMBER, statuses.first { it.name == "Carol" }.limit)
        assertEquals(2, statuses.first { it.name == "Bob" }.attacks)
        assertEquals(5, statuses.first { it.name == "Bob" }.limit)
    }

    @Test
    fun `raid statuses report all done`() {
        val raid = json.decodeFromString<RaidSeasonsDto>(raidJson).items.first()
        val statuses = RaidAnalysis.accountStatuses(listOf(AccountRef("#P1", "Alice")), raid)
        assertTrue(statuses.none { it.open })
    }

    @Test
    fun `clan games diff lists accounts below threshold and missing baselines`() {
        val current = listOf(
            ClanGamesAnalysis.MemberProgress("#P1", "Alice", 55_000),
            ClanGamesAnalysis.MemberProgress("#P2", "Bob", 41_000),
            ClanGamesAnalysis.MemberProgress("#P3", "Carol", 10_000),
        )
        val baseline = mapOf("#P1" to 50_000, "#P2" to 40_000)
        val below = ClanGamesAnalysis.belowThreshold(current, baseline, 4000)
        // Alice earned 5000 (done), Bob 1000 (below), Carol has no baseline
        assertEquals(setOf("Bob", "Carol"), below.map { it.name }.toSet())
        assertEquals(1000, below.first { it.name == "Bob" }.points)
        assertNull(below.first { it.name == "Carol" }.points)
    }

    @Test
    fun `clan games diff empty when every account reached the threshold`() {
        val current = listOf(ClanGamesAnalysis.MemberProgress("#P1", "Alice", 55_000))
        val baseline = mapOf("#P1" to 50_000)
        assertTrue(ClanGamesAnalysis.belowThreshold(current, baseline, 4000).isEmpty())
    }

    @Test
    fun `war analysis false data does not crash account filter`() {
        val war = json.decodeFromString<CurrentWarDto>(warJson)
        val side = WarAnalysis.ourSide(war, "#AAA")!!
        assertFalse(WarAnalysis.openAccountAttacks(side, 2, emptyList()).isNotEmpty())
    }
}
