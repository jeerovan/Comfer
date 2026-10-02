package com.jeerovan.comfer

import com.jeerovan.comfer.compat.FrameworkCompatibilityTest
import com.jeerovan.comfer.tasks.TaskReminderReceiverTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/** One device execution, many regressions. Do not shard on the Spark plan. */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    AnrDeviceStressTest::class,
    AppRefreshBurstStressTest::class,
    Release51RegressionTest::class,
    Release53RecurrenceTest::class,
    WidgetInflationGuardTest::class,
    TaskReminderReceiverTest::class,
    FrameworkCompatibilityTest::class,
)
class AnrTestLabSuite
