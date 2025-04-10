package com.stephen.debugmanager.data.bean

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class KimiResult(
    val choices: List<KimiChoice>,
    val created: Int,
    val id: String,
    val model: String,
    val `object`: String,
    val usage: KimiUsage
)

@Serializable
data class KimiChoice(
    val finish_reason: String,
    val index: Int,
    val message: KimiResultMessage
)

@Serializable
data class KimiUsage(
    val completion_tokens: Int,
    val prompt_tokens: Int,
    val total_tokens: Int
)

@Serializable
data class KimiResultMessage(
    val content: String,
    val role: String
)