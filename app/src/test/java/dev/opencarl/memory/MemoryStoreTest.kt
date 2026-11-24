package dev.opencarl.memory

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MemoryStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store(): MemoryStore {
        val file = File(folder.root, "memory/entries.json")
        return MemoryStore { file }
    }

    @Test
    fun `entries keep their sequence and survive a reload`() = runTest {
        val first = store()
        first.add("installed nodejs in the userland")
        first.add("user prefers zsh")

        val second = store()
        second.load()

        assertEquals(2, second.entries.value.size)
        assertEquals(listOf(1L, 2L), second.entries.value.map { it.seq })
        assertEquals("user prefers zsh", second.entries.value.last().text)
    }

    @Test
    fun `search ranks keyword matches and ignores unrelated entries`() = runTest {
        val store = store()
        store.add("gradle build failed because of a missing sdk")
        store.add("user likes green terminals")
        store.add("gradle cache lives in ~/.gradle")

        val hits = store.search("gradle sdk")

        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it.text.contains("gradle", ignoreCase = true) })
        assertTrue(hits.none { it.text.contains("terminals") })
    }

    @Test
    fun `digest keeps the newest entries within the budget`() = runTest {
        val store = store()
        repeat(40) { index -> store.add("entry number $index with some padding text") }

        val digest = store.digest(maxChars = 200)

        assertTrue(digest.length <= 200)
        assertTrue(digest.contains("entry number 39"))
        assertTrue(!digest.contains("entry number 0 "))
    }

    @Test
    fun `delete removes only the targeted entry`() = runTest {
        val store = store()
        val keep = store.add("keep me")
        val drop = store.add("drop me")

        store.delete(drop.id)

        assertEquals(listOf(keep.id), store.entries.value.map { it.id })
    }
}
