package de.pixel.clashreminders.api

/**
 * Outcome of a CoC API call. Mirrors the lostmanager distinction between
 * "API answered" (HttpError, e.g. 404 = no war) and "request failed"
 * (NetworkError) — the latter fires reminders conservatively.
 */
sealed class ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>()
    data class HttpError(val code: Int) : ApiResult<Nothing>()
    data class NetworkError(val cause: Exception) : ApiResult<Nothing>()
}

fun <T> ApiResult<T>.valueOrNull(): T? = (this as? ApiResult.Success)?.value
