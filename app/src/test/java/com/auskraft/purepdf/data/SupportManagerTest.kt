package com.auskraft.purepdf.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportManagerTest {
    private val day = 24L * 60 * 60 * 1000

    @Test fun firstPromptRequiresFourteenDays() {
        assertFalse(SupportManager.promptDue(0, null, 13 * day))
        assertTrue(SupportManager.promptDue(0, null, 14 * day))
    }

    @Test fun repeatedPromptUsesActualShownTime() {
        assertFalse(SupportManager.promptDue(0, 20 * day, 33 * day))
        assertTrue(SupportManager.promptDue(0, 20 * day, 34 * day))
    }

    @Test fun clockMovingBackwardsDoesNotTriggerPrompt() {
        assertFalse(SupportManager.promptDue(20 * day, null, 19 * day))
        assertFalse(SupportManager.promptDue(0, 30 * day, 29 * day))
    }
}
