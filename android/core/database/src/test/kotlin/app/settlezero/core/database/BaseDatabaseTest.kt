package app.settlezero.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
abstract class BaseDatabaseTest {

    protected lateinit var database: SettleZeroDatabase

    @Before
    open fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = SettleZeroDatabase.buildInMemory(context)
    }

    @After
    open fun tearDown() {
        database.close()
    }
}

