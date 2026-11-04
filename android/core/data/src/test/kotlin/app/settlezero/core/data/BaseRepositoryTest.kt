package app.settlezero.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.settlezero.core.common.TimeProvider
import app.settlezero.core.database.SettleZeroDatabase
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
abstract class BaseRepositoryTest {

    protected lateinit var database: SettleZeroDatabase
    protected lateinit var timeProvider: TestTimeProvider

    class TestTimeProvider(var time: Long = 1000L) : TimeProvider {
        override fun currentTimeMillis(): Long = time
    }

    @Before
    open fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = SettleZeroDatabase.buildInMemory(context)
        timeProvider = TestTimeProvider(1000L)
    }

    @After
    open fun tearDown() {
        database.close()
    }
}

