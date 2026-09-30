package at.woergoetter.chorely

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Runs the app's instrumented tests on [HiltTestApplication] instead of [ChorelyApplication],
 * so a test can swap modules with `@TestInstallIn`. It does not provide WorkManager's
 * configuration the way the real application does, which is why every test that touches
 * WorkManager initializes the test instance itself before anything asks for one.
 */
class HiltTestRunner : AndroidJUnitRunner() {

    override fun newApplication(classLoader: ClassLoader, className: String, context: Context): Application =
        super.newApplication(classLoader, HiltTestApplication::class.java.name, context)
}
