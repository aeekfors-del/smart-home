package com.besenok.smarthome.domain.model

import org.junit.Assert.*
import org.junit.Test

class DeviceTest {

    @Test
    fun device_creation() {
        val device = Device(
            id = "test.device.1",
            name = "Test Device",
            type = Device.DeviceType.LIGHT,
            controlType = ControlType.SWITCH,
            state = State(value = true, ack = false, timestamp = 0L)
        )

        assertEquals("test.device.1", device.id)
        assertEquals("Test Device", device.name)
        assertEquals(Device.DeviceType.LIGHT, device.type)
        assertEquals(ControlType.SWITCH, device.controlType)
        assertTrue(device.state!!.value as Boolean)
    }

    @Test
    fun device_without_state() {
        val device = Device(
            id = "test.device.2",
            name = "Test Device 2",
            type = Device.DeviceType.SOCKET,
            controlType = ControlType.SWITCH,
            state = null
        )

        assertNull(device.state)
    }
}
