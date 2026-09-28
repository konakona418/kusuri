package moe.lizi.kusuri.domain.log

import java.time.Instant
import moe.lizi.kusuri.domain.model.LogEntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogEntryFormTest {

    private val at = Instant.parse("2026-09-28T02:00:00Z")

    private fun symptomForm() = LogEntryFormState.create(LogEntryType.SYMPTOM, at).copy(symptom = "恶心")
    private fun noteForm() = LogEntryFormState.create(LogEntryType.NOTE, at).copy(note = "今天精神不错")

    @Test
    fun `a filled symptom form is valid and converts with trimmed text`() {
        val form = symptomForm().copy(severity = 4, note = "  饭后  ")

        assertTrue(form.validate().isValid)

        val entry = form.toEntry(existingId = null)
        assertEquals(LogEntryType.SYMPTOM, entry.type)
        assertEquals("恶心", entry.symptom)
        assertEquals(4, entry.severity)
        assertEquals("饭后", entry.note)
        assertEquals(at, entry.at)
    }

    @Test
    fun `symptom name is required`() {
        assertEquals(
            LogFormError.REQUIRED,
            symptomForm().copy(symptom = "   ").validate()[LogFormField.SYMPTOM],
        )
    }

    @Test
    fun `severity must stay within one to five`() {
        assertEquals(
            LogFormError.OUT_OF_RANGE,
            symptomForm().copy(severity = 0).validate()[LogFormField.SEVERITY],
        )
        assertEquals(
            LogFormError.OUT_OF_RANGE,
            symptomForm().copy(severity = 6).validate()[LogFormField.SEVERITY],
        )
    }

    @Test
    fun `note form requires text and ignores symptom fields`() {
        val blank = noteForm().copy(note = " ", symptom = "")
        assertEquals(LogFormError.REQUIRED, blank.validate()[LogFormField.NOTE])

        val entry = noteForm().copy(symptom = "恶心", severity = 5).toEntry(existingId = 7L)
        assertEquals(7L, entry.id)
        assertEquals(LogEntryType.NOTE, entry.type)
        assertNull(entry.symptom)
        assertNull(entry.severity)
        assertEquals("今天精神不错", entry.note)
    }

    @Test
    fun `blank optional note becomes null`() {
        val entry = symptomForm().copy(note = "  ").toEntry(existingId = null)

        assertNull(entry.note)
        assertFalse(entry.id > 0)
    }
}
