package com.moshy.drugcalc.io

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

internal sealed interface RevisionSummary {
    val revision: Int
    val createTime: Instant

    @Serializable
    data class Data(
        override val revision: Int,
        override val createTime: Instant,
        val totalDeleted: Int,
        val totalAdded: Int,
        val parent: Int?,
    ): RevisionSummary

    @Serializable
    data class Config(
        override val revision: Int,
        override val createTime: Instant,
        val size: Int,
        val modified: Int,
    ): RevisionSummary
}