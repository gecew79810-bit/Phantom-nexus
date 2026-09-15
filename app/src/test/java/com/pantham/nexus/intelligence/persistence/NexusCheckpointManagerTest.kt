package com.pantham.nexus.intelligence.persistence

import com.pantham.nexus.intelligence.model.TaskCheckpoint
import com.pantham.nexus.intelligence.model.TaskStatus
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NexusCheckpointManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSaveLoadAndDeleteCheckpoint() = runBlocking {
        val testDir = tempFolder.newFolder("nexus_checkpoints")
        val store = FileCheckpointStore(testDir)

        val checkpoint = TaskCheckpoint(
            taskId = "task_alpha_999",
            status = TaskStatus.RUNNING,
            completedNodeIds = setOf("node_1", "node_2"),
            failedNodeIds = setOf("node_fail"),
            pendingNodeIds = setOf("node_3"),
            updatedAt = 1718000000L
        )

        // Save
        store.save(checkpoint)

        // Verify file exists on disk
        val checkpointFile = File(testDir, "task_alpha_999.json")
        assertTrue("JSON file must be written to disk", checkpointFile.exists())

        // Load
        val loaded = store.load("task_alpha_999")
        assertNotNull(loaded)
        assertEquals("task_alpha_999", loaded?.taskId)
        assertEquals(TaskStatus.RUNNING, loaded?.status)
        assertEquals(setOf("node_1", "node_2"), loaded?.completedNodeIds)
        assertEquals(setOf("node_fail"), loaded?.failedNodeIds)
        assertEquals(setOf("node_3"), loaded?.pendingNodeIds)
        assertEquals(1718000000L, loaded?.updatedAt)

        // Delete
        store.delete("task_alpha_999")
        val deletedCheck = store.load("task_alpha_999")
        assertNull("Checkpoint should be null after deletion", deletedCheck)
    }

    @Test
    fun testLoadNonExistentCheckpoint() = runBlocking {
        val testDir = tempFolder.newFolder("nexus_checkpoints_empty")
        val store = FileCheckpointStore(testDir)

        val nonExistent = store.load("unknown_task")
        assertNull(nonExistent)
    }
}
