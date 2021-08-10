/*
 * Copyright 2016-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("InMemoryChatBenchmark")

package macrobenchmarks.chat

import benchmarks.common.*
import java.io.*
import java.nio.file.*
import java.util.concurrent.*
import kotlin.math.*

private const val CLASS_NAME = "macrobenchmarks.chat.RunChat"

public fun main() {
    val configurationsList = allConfigurations
    val executionTimeMs = configurationsList.size * (WARM_UP_ITERATIONS + ITERATIONS) * BENCHMARK_TIME_MS
    println("${configurationsList.size} benchmarks will be run, benchmarks total time is " +
        "${TimeUnit.MILLISECONDS.toMinutes(executionTimeMs)} minutes")

    Files.createDirectories(Paths.get(BENCHMARK_OUTPUT_FILE).parent)
    val csvHeader = "threads,userCount,maxFriendsPercentage,channel,averageWork,dispatcherType,avgSentMessages,stdSentMessages,avgReceivedMessages,stdReceivedMessages"
    PrintWriter(BENCHMARK_OUTPUT_FILE).use { pw ->
        pw.println(csvHeader)
    }

    for ((benchmark, configuration) in configurationsList.withIndex()) {
        println("${round(benchmark / configurationsList.size.toDouble() * 10000) / 100}% done, running benchmark #${benchmark + 1} with configuration ${configuration.configurationToString()}")

        val exitValue = runProcess(CLASS_NAME, JVM_OPTIONS, configuration.configurationToArgsArray())
        if (exitValue != 0) {
            println("The benchmark couldn't complete properly, will end running benchmarks")
            return
        }
    }
}
