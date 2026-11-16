package app.lighthousecontrol.ble

import app.lighthousecontrol.data.StationPowerState
import app.lighthousecontrol.data.StationType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LighthouseProtocolTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun v1PowerOn_hasReversedId() {
        val expected = bytes(0x12, 0x00, 0x00, 0x00, 0x4D, 0x3C, 0x2B, 0x1A) + ByteArray(12)
        assertArrayEquals(expected, LighthouseProtocol.v1Command(PowerAction.ON, "1A2B3C4D"))
    }

    @Test
    fun v1Sleep_hasReversedId() {
        val expected = bytes(0x12, 0x02, 0x00, 0x01, 0x4D, 0x3C, 0x2B, 0x1A) + ByteArray(12)
        assertArrayEquals(expected, LighthouseProtocol.v1Command(PowerAction.SLEEP, "1a2b3c4d"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun v1Standby_isRejected() {
        LighthouseProtocol.v1Command(PowerAction.STANDBY, "1A2B3C4D")
    }

    @Test
    fun v2Commands() {
        assertArrayEquals(bytes(0x01), LighthouseProtocol.v2Command(PowerAction.ON))
        assertArrayEquals(bytes(0x00), LighthouseProtocol.v2Command(PowerAction.SLEEP))
        assertArrayEquals(bytes(0x02), LighthouseProtocol.v2Command(PowerAction.STANDBY))
    }

    @Test
    fun idNormalization() {
        assertEquals("1A2B3C4D", LighthouseProtocol.normalizeV1Id(" 0x1a2b3c4d "))
        assertNull(LighthouseProtocol.normalizeV1Id("1A2B3C"))
        assertNull(LighthouseProtocol.normalizeV1Id("1A2B3C4G"))
    }

    @Test
    fun typeDetection() {
        assertEquals(StationType.V1, LighthouseProtocol.typeFromName("HTC BS 3C4D"))
        assertEquals(StationType.V2, LighthouseProtocol.typeFromName("LHB-1A2B3C4D"))
        assertNull(LighthouseProtocol.typeFromName("Some headphones"))
    }

    @Test
    fun v2StateParsing() {
        assertEquals(StationPowerState.SLEEP, LighthouseProtocol.parseV2State(bytes(0x00)))
        assertEquals(StationPowerState.STANDBY, LighthouseProtocol.parseV2State(bytes(0x02)))
        assertEquals(StationPowerState.ON, LighthouseProtocol.parseV2State(bytes(0x0B)))
        assertEquals(StationPowerState.BOOTING, LighthouseProtocol.parseV2State(bytes(0x09)))
    }
}
