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

    @Test
    fun `openAttackers lists members below required attacks sorted by map position`() {
        val war = json.decodeFromString<CurrentWarDto>(warJson)
        val side = WarAnalysis.ourSide(war, "#AAA")!!
        val open = WarAnalysis.openAttackers(side, WarAnalysis.requiredAttacks(war))
        assertEquals(listOf("Bob", "Carol"), open.map { it.name })
        assertEquals(listOf(1, 0), open.map { it.attacks })
        assertEquals(2, open.first().required)
    }

    @Test
    fun `ourSide finds the clan on the opponent side too`() {
        val war = json.decodeFromString<CurrentWarDto>(warJson)
        assertEquals("Enemy", WarAnalysis.ourSide(war, "#BBB")?.name)
        assertNull(WarAnalysis.ourSide(war, "#CCC"))
    }

    @Test
    fun `cwl day requires one attack per member`() {
        val war = json.decodeFromString<CurrentWarDto>(warJson)
        val side = WarAnalysis.ourSide(war, "#AAA")!!
        val open = WarAnalysis.openAttackers(side, WarAnalysis.CWL_ATTACKS_PER_DAY)
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
    fun `raid analysis separates non-participants from open attacks`() {
        val raid = json.decodeFromString<RaidSeasonsDto>(raidJson).items.first()
        val clanMembers = listOf(
            de.pixel.clashreminders.api.dto.ClanMemberDto("#P1", "Alice"),
            de.pixel.clashreminders.api.dto.ClanMemberDto("#P2", "Bob"),
            de.pixel.clashreminders.api.dto.ClanMemberDto("#P3", "Carol"),
        )
        val result = RaidAnalysis.analyze(clanMembers, raid)
        assertEquals(listOf("Carol"), result.notAttacked.map { it.name })
        assertEquals(listOf("Bob"), result.openAttacks.map { it.name })
        assertFalse(result.allDone)
    }

    @Test
    fun `raid analysis reports all done`() {
        val raid = json.decodeFromString<RaidSeasonsDto>(raidJson).items.first()
        val clanMembers = listOf(
            de.pixel.clashreminders.api.dto.ClanMemberDto("#P1", "Alice"),
        )
        // Alice has 6/6 attacks; with no other members nothing is open
        val result = RaidAnalysis.analyze(clanMembers, raid.copy(members = raid.members.take(1)))
        assertTrue(result.allDone)
    }

    @Test
    fun `clan games diff lists members below threshold and missing baselines`() {
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
    fun `clan games diff empty when everyone reached the threshold`() {
        val current = listOf(ClanGamesAnalysis.MemberProgress("#P1", "Alice", 55_000))
        val baseline = mapOf("#P1" to 50_000)
        assertTrue(ClanGamesAnalysis.belowThreshold(current, baseline, 4000).isEmpty())
    }
}
