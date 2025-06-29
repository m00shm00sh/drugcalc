package com.moshy.drugcalc.calctest

import com.moshy.containers.copyToSortedSet
import com.moshy.drugcalc.calc.datacontroller.DataController
import com.moshy.drugcalc.dbtest.getTestingDataSource
import com.moshy.drugcalc.types.dataentry.*
import com.moshy.drugcalc.types.datasource.*
import kotlinx.coroutines.runBlocking
import java.util.EnumSet

class DataControllerTestSupport {
    enum class RoFlag {
        SOURCE,
        FORBID_CLEAR,
        CONTROLLER,
    }

    companion object {
        fun newTestController(ro: EnumSet<RoFlag>): DataController =
            testSource(ro).run {
                val controller = DataController(
                    DataController.Config(allowDbClear = RoFlag.FORBID_CLEAR !in ro),
                    this
                )
                controller
            }

        private fun testSource(ro: EnumSet<RoFlag>): DataSourceDelegate {
            val source = getTestingDataSource()
            runBlocking { populateDatabase(source) }
            val newSource =
                if (RoFlag.SOURCE in ro)
                    ReadonlyDataSourceDelegate(source)
                else
                    source
            return newSource
        }

        val expectedCompoundNames by lazy {
            DATA.compounds.keys.map(CompoundName::compound).copyToSortedSet()
        }

        val expectedBlendNames by lazy {
            DATA.blends.keys.copyToSortedSet()
        }

        val expectedFrequencyNames by lazy {
            DATA.frequencies.keys.copyToSortedSet()
        }

        fun RO(ro0: RoFlag, vararg ro: RoFlag) = EnumSet.of(ro0, *ro)
        val RO_NONE = EnumSet.noneOf(RoFlag::class.java)
        val RO_ALL = EnumSet.allOf(RoFlag::class.java)
    }
}