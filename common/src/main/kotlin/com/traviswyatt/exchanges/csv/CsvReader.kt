package com.traviswyatt.exchanges.csv

import java.io.File
import java.nio.charset.Charset

fun File.readCsv(charset: Charset = Charsets.UTF_8, separator: Char = ','): List<List<String>> =
    readLines(charset).map { line -> parseLine(line, separator) }

// https://github.com/JetBrains/kotlin-native/blob/c7588d79286d17ef338fd700e3a2ae536df9824e/samples/csvparser/src/main/kotlin/CsvParser.kt#L20-L39
private fun parseLine(line: String, separator: Char): List<String> {
    val result = mutableListOf<String>()
    val builder = StringBuilder()
    var quotes = 0
    for (ch in line) {
        when {
            ch == '\"' -> {
                quotes++
                builder.append(ch)
            }
            (ch == '\n') || (ch == '\r') -> {
            }
            (ch == separator) && (quotes % 2 == 0) -> {
                result.add(builder.toString())
                builder.setLength(0)
            }
            else -> builder.append(ch)
        }
    }
    result.add(builder.toString())
    return result
}