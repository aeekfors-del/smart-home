package com.besenok.smarthome.ui.common

import org.junit.Assert.*
import org.junit.Test

class UiStateTest {

    @Test
    fun success_state() {
        val state = UiState.Success("test data")
        assertTrue(state is UiState.Success)
        assertEquals("test data", (state as UiState.Success).data)
    }

    @Test
    fun error_state() {
        val state = UiState.Error("error message", 404)
        assertTrue(state is UiState.Error)
        assertEquals("error message", (state as UiState.Error).message)
        assertEquals(404, state.code)
    }
}
