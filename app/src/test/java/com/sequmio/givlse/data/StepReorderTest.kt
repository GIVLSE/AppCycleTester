package com.sequmio.givlse.data
import org.junit.Assert.*
import org.junit.Test
class StepReorderTest {
    private val rows = mapOf(0 to StepBounds(0f,50f), 1 to StepBounds(62f,150f), 2 to StepBounds(224f,70f))
    @Test fun usesActualCenters() {
        assertEquals(0,StepReorder.destination(0,120f,rows))
        assertEquals(1,StepReorder.destination(0,140f,rows))
        assertEquals(2,StepReorder.destination(0,260f,rows))
        assertEquals(0,StepReorder.destination(2,20f,rows))
    }
    @Test fun neighborOffsetsAndNumbers() {
        assertEquals(-62f,StepReorder.neighborOffset(1,0,2,62f),0f)
        assertEquals(82f,StepReorder.neighborOffset(0,2,0,82f),0f)
        assertEquals(2,StepReorder.displayIndex(0,0,2))
        assertEquals(0,StepReorder.displayIndex(1,0,2))
        assertEquals(1,StepReorder.displayIndex(0,2,0))
    }
}
