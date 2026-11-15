package com.sharafzai.keyboard

/**
 * Small offline arithmetic parser for the keyboard's built-in calculator.
 * Supports +, -, ×, *, ÷, /, %, ^, unary signs and parentheses.
 * It deliberately evaluates a restricted grammar instead of executing user input.
 */
internal object KeyboardCalculator {
    fun evaluate(source: String): String? {
        if (source.length !in 1..100) return null
        return runCatching {
            val parser = Parser(source.replace('×', '*').replace('÷', '/'))
            val value = parser.parse()
            if (!value.isFinite()) return null
            val normalized = if (value == 0.0) 0.0 else value
            java.math.BigDecimal.valueOf(normalized).stripTrailingZeros().toPlainString()
        }.getOrNull()
    }

    private class Parser(private val input: String) {
        private var position = 0

        fun parse(): Double {
            val result = expression()
            skipSpaces()
            require(position == input.length) { "Unexpected input" }
            return result
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                value = when {
                    consume('+') -> value + term()
                    consume('-') -> value - term()
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = unary()
            while (true) {
                skipSpaces()
                when {
                    consume('*') -> value *= unary()
                    consume('/') -> {
                        val divisor = unary()
                        require(divisor != 0.0) { "Division by zero" }
                        value /= divisor
                    }
                    consume('%') -> {
                        val divisor = power()
                        require(divisor != 0.0) { "Division by zero" }
                        value %= divisor
                    }
                    else -> return value
                }
            }
        }

        private fun power(): Double {
            val base = primary()
            skipSpaces()
            return if (consume('^')) Math.pow(base, unary()) else base
        }

        private fun unary(): Double {
            skipSpaces()
            return when {
                consume('+') -> unary()
                consume('-') -> -unary()
                else -> power()
            }
        }

        private fun primary(): Double {
            skipSpaces()
            if (consume('(')) {
                val value = expression()
                skipSpaces()
                require(consume(')')) { "Missing closing parenthesis" }
                return value
            }
            val start = position
            var decimalSeen = false
            var digitSeen = false
            while (position < input.length) {
                val ch = input[position]
                when {
                    ch in '0'..'9' -> { digitSeen = true; position++ }
                    ch == '.' && !decimalSeen -> { decimalSeen = true; position++ }
                    else -> break
                }
            }
            require(digitSeen) { "Number expected" }
            return input.substring(start, position).toDouble()
        }

        private fun consume(character: Char): Boolean {
            if (position < input.length && input[position] == character) {
                position++
                return true
            }
            return false
        }

        private fun skipSpaces() {
            while (position < input.length && input[position].isWhitespace()) position++
        }
    }
}
