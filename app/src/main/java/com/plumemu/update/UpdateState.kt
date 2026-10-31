package com.plumemu.update

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * How the device is connected right now, as far as updates care. [Wifi] is Wi-Fi or Ethernet and
 * not metered: the only network the daily check uses ("on Wi-Fi"). [Unmetered] is any other
 * unmetered network (an update may download there without asking); [Metered] asks first.
 */
enum class NetworkKind { None, Metered, Unmetered, Wifi }

/** Why a check failed; each has its own plain message. */
enum class CheckFailure { Offline, Timeout, Http, BadManifest, Unexpected }

/** What the last check ended with. */
sealed interface CheckOutcome {
    data object UpToDate : CheckOutcome

    data object Found : CheckOutcome

    /** The latest release needs a newer Android than this device's. */
    data object NeedsNewerAndroid : CheckOutcome

    data class Failed(val failure: CheckFailure) : CheckOutcome
}

/** The last check that ran, successful or not, and when (wall-clock milliseconds). */
data class LastCheck(
    val at: Long,
    val outcome: CheckOutcome,
)

/**
 * What the update feature keeps between runs, in its own file (not a setting, never in a backup:
 * a restored "update found" would offer a release that may be gone).
 * [lastSuccessAt] gates the daily check; [found] lets About show the release without fetching again.
 */
data class UpdateRecord(
    val lastSuccessAt: Long? = null,
    /** The last automatic check that failed; the next automatic try waits [UpdateGate.RETRY_MS]. Cleared by a success. */
    val lastAutoFailureAt: Long? = null,
    val lastCheck: LastCheck? = null,
    val found: UpdateManifest? = null,
)

/** Where [UpdateRecord] is kept; the app's is [FileUpdateStore], tests use a map. */
interface UpdateStore {
    /** The stored record, or an empty one when there is none or it cannot be read (logged). */
    fun read(): UpdateRecord

    /** Throws [IOException] when it cannot be written. */
    fun write(record: UpdateRecord)
}

/**
 * When the daily check is due: the switch is on, the
 * network is Wi-Fi or Ethernet and not metered, the last successful check is 24 h or more ago by the
 * wall clock, and the last failed automatic check (if any since) is [RETRY_MS] or more ago, so a
 * broken server or a captive portal is not asked on every return. A clock that moved back (now
 * before either time) counts as due: a wrong clock must not stop checks for good.
 */
object UpdateGate {
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val RETRY_MS = 6L * 60 * 60 * 1000

    fun isDailyCheckDue(
        checkDaily: Boolean,
        network: NetworkKind,
        lastSuccessAt: Long?,
        now: Long,
        lastAutoFailureAt: Long? = null,
    ): Boolean {
        if (!checkDaily || network != NetworkKind.Wifi) return false
        if (lastAutoFailureAt != null && now >= lastAutoFailureAt && now - lastAutoFailureAt < RETRY_MS) return false
        if (lastSuccessAt == null || now < lastSuccessAt) return true
        return now - lastSuccessAt >= DAY_MS
    }

    /** The person's Check for updates: any network will do; the switch and the 24 h are not asked. */
    fun canCheckNow(network: NetworkKind): Boolean = network != NetworkKind.None
}

/** [UpdateRecord] as a small JSON file, written whole through [AtomicFile] (a crash leaves the old one). */
class FileUpdateStore(
    file: File,
    private val log: (String) -> Unit,
) : UpdateStore {
    private val atomic = AtomicFile(file)

    override fun read(): UpdateRecord =
        try {
            decode(String(atomic.readFully(), Charsets.UTF_8))
        } catch (e: java.io.FileNotFoundException) {
            UpdateRecord()
        } catch (e: IOException) {
            log("Update record unreadable (${e.javaClass.simpleName}); starting empty")
            UpdateRecord()
        } catch (e: JSONException) {
            log("Update record malformed; starting empty")
            UpdateRecord()
        }

    override fun write(record: UpdateRecord) {
        atomic.baseFile.parentFile?.mkdirs()
        val out = atomic.startWrite()
        try {
            out.write(encode(record).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(out)
        } catch (e: IOException) {
            atomic.failWrite(out)
            throw e
        }
    }

    companion object {
        internal fun encode(record: UpdateRecord): String {
            val root = JSONObject()
            record.lastSuccessAt?.let { root.put("lastSuccessAt", it) }
            record.lastAutoFailureAt?.let { root.put("lastAutoFailureAt", it) }
            record.lastCheck?.let { check ->
                root.put("lastCheckAt", check.at)
                root.put("lastOutcome", outcomeName(check.outcome))
            }
            record.found?.let { m ->
                root.put(
                    "found",
                    JSONObject()
                        .put("versionCode", m.versionCode)
                        .put("versionName", m.versionName)
                        .put("apkUrl", m.apkUrl)
                        .put("size", m.size)
                        .put("sha256", m.sha256)
                        .put("minSdk", m.minSdk)
                        .put("released", m.released)
                        .put("notes", JSONArray(m.notes))
                        .put("notesUrl", m.notesUrl ?: JSONObject.NULL),
                )
            }
            return root.toString()
        }

        internal fun decode(text: String): UpdateRecord {
            val root = JSONObject(text)
            val lastCheck =
                if (root.has("lastCheckAt")) {
                    outcomeOf(root.optString("lastOutcome"))?.let { LastCheck(root.getLong("lastCheckAt"), it) }
                } else {
                    null
                }
            val found =
                root.optJSONObject("found")?.let { f ->
                    val notes = f.getJSONArray("notes")
                    UpdateManifest(
                        versionCode = f.getLong("versionCode"),
                        versionName = f.getString("versionName"),
                        apkUrl = f.getString("apkUrl"),
                        size = f.getLong("size"),
                        sha256 = f.getString("sha256"),
                        minSdk = f.getInt("minSdk"),
                        released = f.getString("released"),
                        notes = List(notes.length()) { notes.getString(it) },
                        notesUrl = if (f.isNull("notesUrl")) null else f.getString("notesUrl"),
                    )
                }
            return UpdateRecord(
                lastSuccessAt = if (root.has("lastSuccessAt")) root.getLong("lastSuccessAt") else null,
                lastAutoFailureAt = if (root.has("lastAutoFailureAt")) root.getLong("lastAutoFailureAt") else null,
                lastCheck = lastCheck,
                found = found,
            )
        }

        private fun outcomeName(outcome: CheckOutcome): String =
            when (outcome) {
                CheckOutcome.UpToDate -> "upToDate"
                CheckOutcome.Found -> "found"
                CheckOutcome.NeedsNewerAndroid -> "needsNewerAndroid"
                is CheckOutcome.Failed -> "failed:" + outcome.failure.name
            }

        private fun outcomeOf(name: String): CheckOutcome? =
            when {
                name == "upToDate" -> CheckOutcome.UpToDate
                name == "found" -> CheckOutcome.Found
                name == "needsNewerAndroid" -> CheckOutcome.NeedsNewerAndroid
                name.startsWith("failed:") -> CheckFailure.entries.firstOrNull { it.name == name.removePrefix("failed:") }?.let { CheckOutcome.Failed(it) }
                else -> null
            }
    }
}
