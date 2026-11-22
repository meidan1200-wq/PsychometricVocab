package com.example.psychometricvocab

import android.app.Activity
import com.example.psychometricvocab.data.Word
import com.example.psychometricvocab.data.sync.CloudLayout
import com.example.psychometricvocab.data.sync.CloudSync
import com.example.psychometricvocab.data.sync.Layout
import com.example.psychometricvocab.data.sync.RemoteProgress
import com.example.psychometricvocab.data.sync.RemoteV2
import com.example.psychometricvocab.data.sync.SignInResult
import com.example.psychometricvocab.data.sync.SyncRecord
import com.example.psychometricvocab.data.sync.SyncUser
import com.example.psychometricvocab.data.sync.VocabMigration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The migration tool against an in-memory cloud: the dry run writes nothing, the write needs the
 * typed confirmation and a dry run first, the backup is saved before anything is written, and the
 * layout is only set after a clean read-back.
 */
// Real stableIds are 12 hex characters; the member-key rule of the v2 layout rejects anything else.
private const val ID_A = "a00000000001"
private const val ID_B = "b00000000002"
private const val ID_C = "c00000000003"

class VocabMigrationTest {
    private class FakeCloud(val v1Words: List<SyncRecord>, val v1Days: Map<String, Int>) : CloudSync {
        var layout: Layout? = Layout.V1
        val v2Words = LinkedHashMap<String, SyncRecord>()
        val v2Days = HashMap<String, Int>()
        var bucketPushes = 0
        var dayPushes = 0
        var layoutEntries = ArrayList<Pair<Layout, Map<String, Any>>>()
        var stampClockSeen: Boolean? = null
        var dropMembers = false   // simulates a write that never lands

        override val user: StateFlow<SyncUser?> = MutableStateFlow(SyncUser("u", null))
        override val isAvailable = true
        override suspend fun signIn(activity: Activity): SignInResult = SignInResult.Unavailable
        override suspend fun signOut() {}
        override suspend fun push(records: List<SyncRecord>) = error("v1 must never be written by the migration")
        override suspend fun pushActivity(counts: Map<String, Int>) = error("v1 must never be written by the migration")
        override suspend fun pull(sinceMicros: Long?, stampClock: Boolean): RemoteProgress {
            stampClockSeen = stampClock
            return RemoteProgress(v1Words, v1Days, null, v1Words.size + v1Days.size)
        }
        override fun onChange(sinceMicros: Long?, callback: (RemoteProgress) -> Unit): () -> Unit = {}
        override suspend fun readLayout(): Layout? = layout
        override suspend fun pullV2(sinceWordsMicros: Long?, sinceDaysMicros: Long?): RemoteV2 =
            RemoteV2(v2Words.values.toList(), v2Days.toMap(), null, null, v2Words.size + v2Days.size)
        override suspend fun pushBuckets(writes: List<CloudLayout.BucketWrite>) {
            bucketPushes++
            if (dropMembers) return
            for (w in writes) for (m in w.members.values) CloudLayout.recordOf(m)?.let { v2Words[it.stableId] = it }
        }
        override suspend fun pushDays(days: Map<String, Map<String, Int>>) {
            dayPushes++
            if (!dropMembers) v2Days.putAll(CloudLayout.flattenDays(days))
        }
        override suspend fun registerDevice(deviceId: String, name: String) {}
        override suspend fun setLayoutEntry(layout: Layout, migration: Map<String, Any>) {
            layoutEntries.add(layout to migration)
            this.layout = layout
        }
    }

    private fun record(id: String, updatedAt: Long = 5_000) = SyncRecord(id, 1.5f, 2.5f, 6, 1_700_000_000_000L, 3, 1, true, updatedAt)

    private fun word(id: Int, stableId: String, track: String, unit: Int) =
        Word(id = id, track = track, word = "w$id", definition = "d$id", unit = unit, stableId = stableId)

    private val words = listOf(word(1, ID_A, "english", 1), word(2, ID_B, "english", 1), word(3, ID_C, "hebrew", 2))

    private fun cloud() = FakeCloud(listOf(record(ID_A), record(ID_B), record(ID_C)), mapOf("2026-11-01" to 5, "2026-11-22" to 40))

    private fun tool(cloud: FakeCloud, dir: File) = VocabMigration(cloud, { words }, dir, { "dev1" })

    private fun tempDir(): File = Files.createTempDirectory("migration").toFile()

    @Test
    fun theDryRunWritesNothingAndReportsThePlan() = runBlocking {
        val c = cloud()
        val dir = tempDir()
        val report = tool(c, dir).dryRun()
        assertTrue(report.ok)
        assertEquals("no clock document either", false, c.stampClockSeen)
        assertEquals(0, c.bucketPushes + c.dayPushes + c.layoutEntries.size)
        assertTrue(report.text.contains("nothing was written"))
        assertTrue(report.text.contains("english-u1: 2 word(s)"))
        assertTrue(report.text.contains("hebrew-u2: 1 word(s)"))
        assertFalse("not even a backup folder is created by a dry run", File(dir, "backups").exists())
    }

    @Test
    fun noWriteWithoutADryRunFirst() = runBlocking {
        val c = cloud()
        val report = tool(c, tempDir()).applyBesideV1(VocabMigration.CONFIRM_WRITE)
        assertFalse(report.ok)
        assertEquals(0, c.bucketPushes)
    }

    @Test
    fun noWriteWithoutTheTypedConfirmation() = runBlocking {
        val c = cloud()
        val t = tool(c, tempDir())
        t.dryRun()
        assertFalse(t.applyBesideV1("yes").ok)
        assertFalse(t.applyBesideV1("").ok)
        assertEquals(0, c.bucketPushes + c.dayPushes)
    }

    @Test
    fun theWriteSavesTheBackupFirstThenWritesV2BesideV1AndVerifiesZeroDifferences() = runBlocking {
        val c = cloud()
        val dir = tempDir()
        val t = tool(c, dir)
        t.dryRun()
        val report = t.applyBesideV1(VocabMigration.CONFIRM_WRITE)
        assertTrue(report.text, report.ok)
        assertTrue(report.text.contains("0 differences: verified"))
        assertEquals(3, c.v2Words.size)
        assertEquals(mapOf("2026-11-01" to 5, "2026-11-22" to 40), c.v2Days)
        assertEquals("the layout is NOT set by the write", 0, c.layoutEntries.size)
        val backup = File(dir, "backups").listFiles()!!.single()
        val text = backup.readText()
        assertTrue(listOf(ID_A, ID_B, ID_C).all { text.contains("\"stableId\":\"$it\"") })
        assertTrue(text.contains("\"2026-11-22\":40"))
    }

    @Test
    fun aWriteThatDoesNotLandIsNotVerifiedAndTheLayoutCanNotBeSet() = runBlocking {
        val c = cloud().apply { dropMembers = true }
        val t = tool(c, tempDir())
        t.dryRun()
        val report = t.applyBesideV1(VocabMigration.CONFIRM_WRITE)
        assertFalse(report.ok)
        assertTrue(report.text.contains("NOT verified"))
        assertFalse(t.setDual(VocabMigration.CONFIRM_DUAL).ok)
        assertEquals(0, c.layoutEntries.size)
    }

    @Test
    fun theLayoutIsSetToDualOnlyAfterAVerifiedWriteAndTheTypedConfirmation() = runBlocking {
        val c = cloud()
        val t = tool(c, tempDir())
        assertFalse("before anything", t.setDual(VocabMigration.CONFIRM_DUAL).ok)
        t.dryRun()
        assertFalse("before the write", t.setDual(VocabMigration.CONFIRM_DUAL).ok)
        t.applyBesideV1(VocabMigration.CONFIRM_WRITE)
        assertFalse("without the confirmation", t.setDual("dual").ok)
        assertEquals(0, c.layoutEntries.size)
        val done = t.setDual(VocabMigration.CONFIRM_DUAL)
        assertTrue(done.text, done.ok)
        val (layout, migration) = c.layoutEntries.single()
        assertEquals(Layout.DUAL, layout)
        assertEquals("vocab-dev1", migration["by"])
        assertTrue((migration["backup"] as String).contains("cloud-v1-"))
        assertEquals(mapOf("words" to 3, "days" to 2, "buckets" to 2), migration["counts"])
    }

    @Test
    fun theLayoutIsNotSetIfV2ChangedBetweenTheCheckAndTheSwitch() = runBlocking {
        val c = cloud()
        val t = tool(c, tempDir())
        t.dryRun()
        t.applyBesideV1(VocabMigration.CONFIRM_WRITE)
        c.v2Words[ID_A] = record(ID_A, updatedAt = 99_999)   // someone changed v2 in between
        val report = t.setDual(VocabMigration.CONFIRM_DUAL)
        assertFalse(report.ok)
        assertEquals(0, c.layoutEntries.size)
    }

    @Test
    fun anAccountAlreadyOnV2IsNotWrittenTo() = runBlocking {
        val c = cloud().apply { layout = Layout.V2 }
        val t = tool(c, tempDir())
        t.dryRun()
        assertFalse(t.applyBesideV1(VocabMigration.CONFIRM_WRITE).ok)
        assertEquals(0, c.bucketPushes)
    }

    @Test
    fun anUnreadableCloudChangesNothing() = runBlocking {
        val c = object : CloudSyncDelegate(cloud()) {
            override suspend fun pull(sinceMicros: Long?, stampClock: Boolean): RemoteProgress? = null
        }
        val report = VocabMigration(c, { words }, tempDir()).dryRun()
        assertFalse(report.ok)
        assertTrue(report.text.contains("Nothing was changed"))
    }

    /** A CloudSync that forwards to another, so one method can be overridden. */
    private open class CloudSyncDelegate(private val d: FakeCloud) : CloudSync by d
}
