package com.taomic.agent.recorder

import com.taomic.agent.skill.dsl.SkillSpec
import com.taomic.agent.skill.dsl.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * SkillStore 单元测试。使用临时目录替代 Android Context。
 */
class SkillStoreTest {

    private lateinit var tempDir: File
    private lateinit var store: SkillStore

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "skill_store_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        store = SkillStore(directory = tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `save and load round-trip`() {
        val spec = SkillSpec(
            id = "test_skill",
            name = "Test Skill",
            description = "A test skill",
            steps = listOf(Step.LaunchApp(packageName = "com.example.app")),
        )
        assertTrue(store.save(spec))
        val loaded = store.load("test_skill")
        assertNotNull(loaded)
        assertEquals("test_skill", loaded!!.id)
        assertEquals("Test Skill", loaded.name)
        assertEquals(1, loaded.steps.size)
    }

    @Test
    fun `load non-existent returns null`() {
        assertNull(store.load("non_existent"))
    }

    @Test
    fun `listAll returns saved skills`() {
        val spec1 = SkillSpec(id = "skill_a", name = "A", steps = listOf(Step.Sleep(ms = 100)))
        val spec2 = SkillSpec(id = "skill_b", name = "B", steps = listOf(Step.Sleep(ms = 200)))
        store.save(spec1)
        store.save(spec2)

        val all = store.listAll()
        assertEquals(2, all.size)
        val ids = all.map { it.id }.toSet()
        assertTrue(ids.contains("skill_a"))
        assertTrue(ids.contains("skill_b"))
    }

    @Test
    fun `delete removes skill`() {
        val spec = SkillSpec(id = "to_delete", name = "Delete Me", steps = emptyList())
        store.save(spec)
        assertTrue(store.exists("to_delete"))
        assertTrue(store.delete("to_delete"))
        assertFalse(store.exists("to_delete"))
    }

    @Test
    fun `exists returns false for non-existent`() {
        assertFalse(store.exists("no_such_skill"))
    }

    @Test
    fun `listAll on empty directory returns empty list`() {
        assertEquals(0, store.listAll().size)
    }
}
