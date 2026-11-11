package io.github.baslawson.smartnotes

import io.github.baslawson.smartnotes.data.AppVersion
import io.github.baslawson.smartnotes.data.ReleaseApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test fun versionsCompareByNumber() {
        assertTrue(AppVersion.newer("0.1.10", "0.1.9"))
        assertTrue(AppVersion.newer("v0.2", "0.1.9"))
        assertFalse(AppVersion.newer("0.1.0", "0.1"))
        assertFalse(AppVersion.newer("0.1.0-beta", "0.0.1"))
    }

    @Test fun sha256File() {
        val hash = "a".repeat(64)
        assertEquals(hash, AppVersion.sha256Of("${hash.uppercase()}  SmartNotes.apk\n"))
        assertNull(AppVersion.sha256Of("abc SmartNotes.apk"))
    }

    private fun release(tag: String, vararg assets: String, extra: String = "") =
        """{"tag_name":"$tag","body":"Notes","html_url":"https://github.com/x"$extra,"assets":[""" +
            assets.joinToString(",") { """{"name":"$it","browser_download_url":"https://example.org/$it"}""" } + "]}"

    @Test fun releaseNeedsTheApkAndItsChecksum() {
        val r = ReleaseApi.parse(release("v0.2.0", "SmartNotes.apk", "SmartNotes.apk.sha256"))!!
        assertEquals("0.2.0", r.version)
        assertEquals("https://example.org/SmartNotes.apk", r.apkUrl)
        assertNull(ReleaseApi.parse(release("v0.2.0", "SmartNotes.apk")))
        assertNull(ReleaseApi.parse(release("v0.2.0", "Planner.apk", "Planner.apk.sha256")))
        assertNull(ReleaseApi.parse(release("latest", "SmartNotes.apk", "SmartNotes.apk.sha256")))
        assertNull(ReleaseApi.parse(release("v0.2.0", "SmartNotes.apk", "SmartNotes.apk.sha256", extra = ""","prerelease":true""")))
    }
}
