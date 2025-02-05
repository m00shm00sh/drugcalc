package com.moshy.drugcalc.db

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.json.json
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

import com.moshy.drugcalc.calcdata.CompoundInfo
import com.moshy.drugcalc.calcdata.BlendValue
import com.moshy.drugcalc.calcdata.FrequencyValue
import com.moshy.drugcalc.io.ConfigMap
import com.moshy.drugcalc.io.FullDiff
import com.moshy.drugcalc.io.JsonWithStrictIsoDuration
import kotlinx.datetime.Instant

internal object DataObjects : Table("data_objs"), HasRevisionAndCreateTime {
    override val revision = integer("revision")
    override val createTime = timestamp("created")

    val diffCompounds =
        json<FullDiff<CompoundInfo>>("d_compounds", JsonWithStrictIsoDuration)
        .default(FullDiff())
    val diffBlends =
        json<FullDiff<BlendValue>>("d_blends", JsonWithStrictIsoDuration)
        .default(FullDiff())
    val diffFrequencies =
        json<FullDiff<FrequencyValue>>("d_frequencies", JsonWithStrictIsoDuration)
        .default(FullDiff())

    val revoked = bool("revoked").default(false)
    val parent = integer("parent").index("i_d_parent").references(revision).nullable()

    override val primaryKey = PrimaryKey(revision)
}

internal object ConfigObjects : Table("config_objs"), HasRevisionAndCreateTime {
    override val revision = integer("revision")
    override val createTime = timestamp("created")

    /* Store as Map so we can add or remove fields of Config without rebuilding table
     * NOTE: this still a full state (with possibly extra or missing keys and not a delta from prevision revision
     */

    val config = json<ConfigMap>("config", JsonWithStrictIsoDuration)

    override val primaryKey = PrimaryKey(revision)
}

internal enum class ObjectType {
    DATA,
    CONFIG
}
internal object Caches : Table("caches") {
    val objType = enumeration("o_type", ObjectType::class).uniqueIndex("iu_otype")
    // we can't do a FK constraint because it depends on value of `objType`
    val revision = integer("revision")
    // can't do json here because deserializer to use depends on value of `objType`
    val data = text("content")
}

internal interface HasRevisionAndCreateTime {
    val revision: Column<Int>
    val createTime: Column<Instant>
}

fun <T> blockingTransaction(db: Database, block: Transaction.() -> T): T =
    transaction(db, statement = block)
suspend fun <T> suspendTransaction(db: Database, block: suspend Transaction.() -> T): T =
    newSuspendedTransaction(Dispatchers.IO, db, statement = block)