package com.example.psychometricvocab.data.sync

import android.util.Log
import com.example.psychometricvocab.data.Word
import java.io.File

/**
 * The tool that moves one account from the flat v1 collection to the v2 layout. It is only BUILT:
 * nothing calls it except a deliberate action, and it writes to the cloud only on an explicit
 * typed confirmation, in this order, each step available only after the one before it:
 *
 *  1. [dryRun]: reads v1 (and whatever v2 holds) and the phone's word list, plans the v2 layout and
 *     reports counts per bucket, sizes, words it can't place, key collisions and the diff against
 *     v2. It WRITES NOTHING (not even the clock document a normal full pull stamps).
 *  2. [applyBesideV1]: exports every v1 document it read to a JSON file on the phone first (and
 *     stops if that fails), then writes v2 BESIDE v1 (v1 is never changed), reads v2 back and
 *     compares every word field by field and every activity day. Accepted only at 0 differences.
 *  3. [setDual]: after a clean read-back it re-checks once more, then sets apps.vocab.layout to
 *     'dual' in users/{uid}. Until this step every device still uses v1 and nothing else changes.
 *
 * Rolling back before the layout is set is free: v1 was never touched and v2 is derived.
 */
class VocabMigration(
    private val cloud: CloudSync,
    private val localWords: suspend () -> List<Word>,
    private val filesDir: File,
    private val deviceId: () -> String = { "phone" }
) {
    data class Report(val ok: Boolean, val text: String)

    // What this session's dry run saw; apply and setDual refuse to run without it.
    private var dry: DryRunData? = null
    private var verified = false
    private var backupPath: String? = null

    private class DryRunData(val v1: RemoteProgress, val plan: V2Migration.Plan)

    suspend fun dryRun(): Report {
        verified = false
        val v1 = cloud.pull(null, stampClock = false) ?: return fail("Could not read the cloud (offline?). Nothing was changed.")
        val v2 = cloud.pullV2(null, null) ?: return fail("Could not read the v2 space. Nothing was changed.")
        val layout = cloud.readLayout()
        val words = localWords()
        val plan = V2Migration.plan(v1.words, v1.activity, CloudLayout.placement(words), CloudLayout.capacities(words))
        val diff = V2Migration.compare(v1.words, v1.activity, v2.words, v2.activity)
        dry = DryRunData(v1, plan)

        val sb = StringBuilder()
        sb.appendLine("DRY RUN: nothing was written.")
        sb.appendLine("Layout now: ${layout?.wire ?: "unknown (could not be read)"}")
        sb.appendLine("v1 in the cloud: ${plan.v1Words} word record(s), ${plan.v1Days} activity day(s); ${v1.docsRead} doc(s) read")
        sb.appendLine("v2 plan: ${plan.writes.size} bucket(s), ${plan.days.size} month document(s)")
        for (s in plan.stats) {
            sb.appendLine("  ${s.id}: ${s.words} word(s) of ${s.capacity}, ~${s.estimatedBytes / 1024} KB of ${CloudLayout.MAX_BUCKET_BYTES / 1024} KB")
        }
        sb.appendLine("Words this phone can't place: ${plan.unplaced.size}" + if (plan.unplaced.isNotEmpty()) " (they would go to ${CloudLayout.UNPLACED_BUCKET})" else "")
        sb.appendLine("Member-key collisions: ${plan.keyCollisions.size}" + plan.keyCollisions.take(5).joinToString("") { "\n  $it" })
        sb.appendLine("Buckets over the size guard: ${plan.refused.size}" + if (plan.refused.isNotEmpty()) " ${plan.refused}" else "")
        sb.appendLine("Words with an invalid member key: ${plan.invalid.size}" + plan.invalid.take(5).joinToString("") { "\n  $it" })
        sb.appendLine("v2 as it is now: ${v2.words.size} word(s), ${v2.activity.size} day(s) (${v2.docsRead} doc(s) read)")
        sb.appendLine("Difference v1 vs v2 now: ${diff.missingInV2.size} missing in v2, ${diff.extraInV2.size} extra in v2, ${diff.different.size} different, ${diff.daysDifferent.size} day(s) different")
        sb.append(if (plan.safeToWrite) "Safe to write beside v1." else "NOT safe to write: fix the collisions, invalid keys or oversized buckets first.")
        return Report(plan.safeToWrite, sb.toString())
    }

    /** Step 2. [confirmation] must be [CONFIRM_WRITE] exactly. */
    suspend fun applyBesideV1(confirmation: String): Report {
        if (confirmation != CONFIRM_WRITE) return fail("Not written: type $CONFIRM_WRITE to confirm.")
        val d = dry ?: return fail("Not written: run the dry run first (in this session).")
        if (!d.plan.safeToWrite) return fail("Not written: the dry run found problems.")
        val layout = cloud.readLayout() ?: return fail("Not written: could not read the layout.")
        if (layout == Layout.V2) return fail("Not written: the account is already on v2.")

        // 1. The backup first: every v1 document read, as a file on the phone. No file, no write.
        val json = V2Migration.exportJson(d.v1.words, d.v1.activity, System.currentTimeMillis())
        val file = try {
            val dir = File(filesDir, "backups").apply { mkdirs() }
            File(dir, "cloud-v1-${System.currentTimeMillis()}.json").also { it.writeText(json) }
        } catch (e: Exception) {
            Log.e(TAG, "backup failed, nothing written", e)
            return fail("Not written: the backup file could not be saved (${e.message}).")
        }
        if (!file.exists() || file.length() != json.toByteArray().size.toLong()) {
            return fail("Not written: the backup file did not check out.")
        }
        backupPath = file.path

        // 2. v2 beside v1 (v1 is not touched).
        cloud.pushBuckets(d.plan.writes)
        cloud.pushDays(d.plan.days)

        // 3. Read back and compare, field by field. Accepted only at 0 differences.
        return verify(d, "Wrote v2 beside v1 (backup: ${file.path}).")
    }

    /** Step 3, only after a clean read-back; [confirmation] must be [CONFIRM_DUAL] exactly. */
    suspend fun setDual(confirmation: String): Report {
        if (confirmation != CONFIRM_DUAL) return fail("Layout not changed: type $CONFIRM_DUAL to confirm.")
        val d = dry ?: return fail("Layout not changed: run the dry run first (in this session).")
        if (!verified) return fail("Layout not changed: v2 has not been verified with 0 differences in this session.")
        // Once more, right before the flip: the read-back must still be clean.
        val again = verify(d, "Re-checked before the switch.")
        if (!again.ok) return Report(false, "Layout not changed. " + again.text)
        cloud.setLayoutEntry(
            Layout.DUAL,
            mapOf(
                "by" to "vocab-${deviceId()}",
                "backup" to (backupPath ?: ""),
                "counts" to mapOf("words" to d.plan.v1Words, "days" to d.plan.v1Days, "buckets" to d.plan.writes.size)
            )
        )
        return Report(true, "Layout set to dual (apps.vocab.layout). Devices now read and write both layouts.")
    }

    private suspend fun verify(d: DryRunData, prefix: String): Report {
        val v2 = cloud.pullV2(null, null) ?: run { verified = false; return fail("$prefix Could not read v2 back; not verified.") }
        val diff = V2Migration.compare(d.v1.words, d.v1.activity, v2.words, v2.activity)
        verified = diff.count == 0
        val text = "$prefix Read back ${v2.words.size} word(s), ${v2.activity.size} day(s): " +
            "${diff.missingInV2.size} missing, ${diff.extraInV2.size} extra, ${diff.different.size} different, ${diff.daysDifferent.size} day(s) different. " +
            if (verified) "0 differences: verified." else "NOT verified (${diff.count} difference(s))."
        return Report(verified, text)
    }

    private fun fail(text: String) = Report(false, text)

    companion object {
        private const val TAG = "VocabMigration"
        const val CONFIRM_WRITE = "WRITE V2 BESIDE V1"
        const val CONFIRM_DUAL = "SET DUAL"
    }
}
