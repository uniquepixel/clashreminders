package de.pixel.clashreminders.api

import de.pixel.clashreminders.api.dto.ClanDto
import de.pixel.clashreminders.api.dto.CurrentWarDto
import de.pixel.clashreminders.api.dto.LeagueGroupDto
import de.pixel.clashreminders.api.dto.PlayerDto
import de.pixel.clashreminders.api.dto.RaidSeasonsDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Clash of Clans API client routed through the RoyaleAPI proxy (fixed IP,
 * so the key works from mobile networks). Same paths and auth as the
 * official API. Retry behaviour ports lostmanager's
 * performHttpRequestWithRetry: 3 attempts with 1s/2s backoff on 5xx/429
 * and network failures; 4xx is returned immediately.
 */
class CocApiClient(private val apiKeyProvider: suspend () -> String?) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun getClan(tag: String): ApiResult<ClanDto> =
        get("/clans/${encode(tag)}")

    suspend fun getCurrentWar(tag: String): ApiResult<CurrentWarDto> =
        get("/clans/${encode(tag)}/currentwar")

    suspend fun getLeagueGroup(tag: String): ApiResult<LeagueGroupDto> =
        get("/clans/${encode(tag)}/currentwar/leaguegroup")

    suspend fun getCwlWar(warTag: String): ApiResult<CurrentWarDto> =
        get("/clanwarleagues/wars/${encode(warTag)}")

    suspend fun getRaidSeasons(tag: String): ApiResult<RaidSeasonsDto> =
        get("/clans/${encode(tag)}/capitalraidseasons?limit=1")

    suspend fun getPlayer(tag: String): ApiResult<PlayerDto> =
        get("/players/${encode(tag)}")

    /** Cheap key check: clan search succeeds iff the key/IP combination is valid. */
    suspend fun testKey(): ApiResult<Unit> =
        when (val result = rawGet("/clans?minMembers=40&limit=1")) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }

    private suspend inline fun <reified T> get(path: String): ApiResult<T> =
        when (val result = rawGet(path)) {
            is ApiResult.Success -> try {
                ApiResult.Success(json.decodeFromString<T>(result.value))
            } catch (e: Exception) {
                ApiResult.NetworkError(IOException("Failed to parse response for $path", e))
            }
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }

    private suspend fun rawGet(path: String): ApiResult<String> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
            ?: return@withContext ApiResult.HttpError(403)

        var lastFailure: ApiResult<String> = ApiResult.NetworkError(IOException("not attempted"))
        for (attempt in 0 until MAX_ATTEMPTS) {
            if (attempt > 0) delay(RETRY_DELAYS_MS[attempt - 1])
            val request = Request.Builder()
                .url(BASE_URL + path)
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> return@withContext ApiResult.Success(body)
                        response.code >= 500 || response.code == 429 ->
                            lastFailure = ApiResult.HttpError(response.code)
                        else -> return@withContext ApiResult.HttpError(response.code)
                    }
                }
            } catch (e: IOException) {
                lastFailure = ApiResult.NetworkError(e)
            }
        }
        lastFailure
    }

    private fun encode(tag: String): String = URLEncoder.encode(tag, "UTF-8")

    companion object {
        const val BASE_URL = "https://cocproxy.royaleapi.dev/v1"
        private const val MAX_ATTEMPTS = 3
        private val RETRY_DELAYS_MS = longArrayOf(1000, 2000)
    }
}
