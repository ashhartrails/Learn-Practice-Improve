package com.example.learnanything.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.learnanything.core.data.settings.SecretCipher
import com.example.learnanything.core.data.settings.SettingsRepository
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Reversible stand-in; the real cipher needs the Android Keystore. */
    private val cipher = object : SecretCipher {
        override fun encrypt(plain: String) = "enc:" + plain.reversed()
        override fun decrypt(encoded: String) = encoded.removePrefix("enc:").reversed()
    }

    private fun repo(scope: TestScope): Pair<SettingsRepository, File> {
        val file = File(tmp.root, "settings.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { file }
        return SettingsRepository(store, cipher) to file
    }

    @Test fun keyIsStoredEncryptedAndReadBack() = runTest(UnconfinedTestDispatcher()) {
        val (settings, file) = repo(this)
        settings.saveGeminiKey("  AIzaSECRET1234 ")

        assertThat(settings.apiKey()).isEqualTo("AIzaSECRET1234")
        assertThat(settings.geminiKey.first().lastFour).isEqualTo("1234")
        assertThat(file.readBytes().toString(Charsets.UTF_8)).doesNotContain("AIzaSECRET1234")
    }

    @Test fun clearingTheKeyAlsoClearsTheModel() = runTest(UnconfinedTestDispatcher()) {
        val (settings, _) = repo(this)
        settings.saveGeminiKey("k-0001")
        settings.setGeminiModel("nova-flash")
        settings.clearGeminiKey()

        assertThat(settings.apiKey()).isNull()
        assertThat(settings.selectedModel()).isNull()
        assertThat(settings.geminiKey.first().saved).isFalse()
    }
}
