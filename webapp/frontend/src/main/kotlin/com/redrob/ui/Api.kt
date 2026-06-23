package com.redrob.ui

import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlin.js.Promise

// Browser fetch — declared minimally so we don't depend on a particular wrapper surface.
external fun fetch(input: String, init: dynamic = definedExternally): Promise<dynamic>

/** Thin client over the Spring Boot API. */
object Api {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private suspend fun getText(url: String): String {
        val res = fetch(url).await()
        if (res.ok != true) throw RuntimeException("GET $url failed: ${res.status}")
        return (res.text() as Promise<String>).await()
    }

    suspend fun fetchJd(): JobDescription =
        json.decodeFromString(JobDescription.serializer(), getText("/api/jd"))

    suspend fun fetchResults(limit: Int = 100): List<RankedCandidate> =
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(RankedCandidate.serializer()),
            getText("/api/results?limit=$limit"))

    suspend fun fetchCandidate(id: String): CandidateDetail =
        json.decodeFromString(CandidateDetail.serializer(), getText("/api/candidates/$id"))

    /** Kick off a live ranker re-run. Returns the HTTP status (200 started, 409 already running). */
    suspend fun startRun(): Int {
        val res = fetch("/api/rank/run", js("({ method: 'POST' })")).await()
        return res.status as Int
    }
}
