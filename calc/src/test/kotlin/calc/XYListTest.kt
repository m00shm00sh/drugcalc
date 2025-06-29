package calc

import com.moshy.drugcalc.calc.calc.RangeValue
import com.moshy.drugcalc.calc.calc.toXYList
import com.moshy.drugcalc.types.calccommand.XYList
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class XYListTest {
    @Test
    fun testRangeListToXYList() {
        val vals = listOf(
            RangeValue(1.0, 0, 1),
            RangeValue(2.0, 1, 2),
            RangeValue(1.0, 2, 4)
        )
        val xyVals = listOf(
            listOf(0, 1, 2),
            listOf(1.0, 2.0, 1.0)
        )
        val valsAsXY = vals.toXYList()
        assertEquals(XYList.PlotType.BAR, valsAsXY.type)
        assertEquals(xyVals[0].size, valsAsXY.x.size)
        assertIterableEquals(xyVals[0], valsAsXY.x)
        assertIterableEquals(xyVals[1], valsAsXY.y)
    }
}