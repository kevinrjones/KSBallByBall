package com.knowledgespike.ballbyball.parsecricsheet

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isFalse

class LogbackConfigurationTest {
    @Test
    fun `given parser logging configuration then lookup failures use a rotating console and file logger`() {
        val resource = requireNotNull(javaClass.getResource("/logback.xml"))
        val configuration = resource.readText()

        expectThat(configuration)
            .contains("<file>\${LOG_DIR}/lookup_failures.log</file>")
            .contains("<fileNamePattern>\${LOG_DIR}/archived/lookup_failures.%d{yyyy-MM-dd}.%i.log</fileNamePattern>")
            .contains("<logger name=\"com.knowledgespike.ballbyball.parsecricsheet.lookup-failures\"")
            .contains("<appender-ref ref=\"LOOKUP-FAILURES-STDOUT\"/>")
            .contains("<appender-ref ref=\"LOOKUP-FAILURES-FILE\"/>")
            .contains("<maxFileSize>10MB</maxFileSize>")
            .contains("<totalSizeCap>20GB</totalSizeCap>")
            .contains("<maxHistory>60</maxHistory>")
    }

    @Test
    fun `given parser test logging configuration then file appenders are not configured`() {
        val resource = requireNotNull(javaClass.getResource("/logback-test.xml"))
        val configuration = resource.readText()

        expectThat(configuration.contains("<file>")).isFalse()
        expectThat(configuration.contains("RollingFileAppender")).isFalse()
        expectThat(configuration.contains("application.log")).isFalse()
        expectThat(configuration.contains("lookup_failures.log")).isFalse()
        expectThat(configuration).contains("<appender name=\"STDOUT\"")
    }
}