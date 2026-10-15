package com.knowledgespike.ballbyball.parsecricsheet

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isFalse

class LogbackConfigurationTest {
    @Test
    fun `given parser logging configuration then lookup failures split errors from general messages`() {
        val resource = requireNotNull(javaClass.getResource("/logback.xml"))
        val configuration = resource.readText()

        expectThat(configuration)
            .contains("<file>\${LOG_DIR}/lookup_errors.log</file>")
            .contains("<fileNamePattern>\${LOG_DIR}/archived/lookup_errors.%d{yyyy-MM-dd}.%i.log</fileNamePattern>")
            .contains("<file>\${LOG_DIR}/lookup_general.log</file>")
            .contains("<fileNamePattern>\${LOG_DIR}/archived/lookup_general.%d{yyyy-MM-dd}.%i.log</fileNamePattern>")
            .contains("<logger name=\"com.knowledgespike.ballbyball.parsecricsheet.lookup-failures\" level=\"info\"")
            .contains("<appender-ref ref=\"LOOKUP-FAILURES-STDOUT\"/>")
            .contains("<appender-ref ref=\"LOOKUP-ERRORS-FILE\"/>")
            .contains("<appender-ref ref=\"LOOKUP-GENERAL-FILE\"/>")
            .contains("<maxFileSize>10MB</maxFileSize>")
            .contains("<totalSizeCap>20GB</totalSizeCap>")
            .contains("<maxHistory>60</maxHistory>")

        val errorsAppender = configuration.substringAfter("<appender name=\"LOOKUP-ERRORS-FILE\"")
            .substringBefore("</appender>")
        expectThat(errorsAppender)
            .contains("<level>ERROR</level>")
            .contains("<onMatch>ACCEPT</onMatch>")
            .contains("<onMismatch>DENY</onMismatch>")

        val generalAppender = configuration.substringAfter("<appender name=\"LOOKUP-GENERAL-FILE\"")
            .substringBefore("</appender>")
        expectThat(generalAppender)
            .contains("<level>ERROR</level>")
            .contains("<onMatch>DENY</onMatch>")
            .contains("<onMismatch>ACCEPT</onMismatch>")

        expectThat(configuration.contains("lookup_failures.log")).isFalse()
    }

    @Test
    fun `given parser test logging configuration then file appenders are not configured`() {
        val resource = requireNotNull(javaClass.getResource("/logback-test.xml"))
        val configuration = resource.readText()

        expectThat(configuration.contains("<file>")).isFalse()
        expectThat(configuration.contains("RollingFileAppender")).isFalse()
        expectThat(configuration.contains("application.log")).isFalse()
        expectThat(configuration.contains("lookup_errors.log")).isFalse()
        expectThat(configuration.contains("lookup_general.log")).isFalse()
        expectThat(configuration).contains("<appender name=\"STDOUT\"")
    }
}