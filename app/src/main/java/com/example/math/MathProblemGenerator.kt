package com.example.math

import kotlin.random.Random

data class MathProblem(
  val questionText: String,
  val correctAnswer: Int,
  val options: List<Int>,
  val difficulty: String
)

object MathProblemGenerator {

  const val DIFF_EASY = "EASY"
  const val DIFF_MEDIUM = "MEDIUM"
  const val DIFF_HARD = "HARD"
  const val DIFF_EXPERT = "EXPERT"
  const val DIFF_NIGHTMARE = "NIGHTMARE"

  fun getDifficultyLabel(difficulty: String): String {
    return when (difficulty) {
      DIFF_EASY -> "簡単"
      DIFF_MEDIUM -> "普通"
      DIFF_HARD -> "難しい"
      DIFF_EXPERT -> "超難問"
      DIFF_NIGHTMARE -> "鬼畜・極限"
      else -> "普通"
    }
  }

  fun getNextDifficulty(current: String): String {
    return when (current) {
      DIFF_EASY -> DIFF_MEDIUM
      DIFF_MEDIUM -> DIFF_HARD
      DIFF_HARD -> DIFF_EXPERT
      DIFF_EXPERT -> DIFF_NIGHTMARE
      DIFF_NIGHTMARE -> DIFF_NIGHTMARE
      else -> DIFF_HARD
    }
  }

  fun generate(difficulty: String): MathProblem {
    return when (difficulty) {
      DIFF_EASY -> generateEasy()
      DIFF_MEDIUM -> generateMedium()
      DIFF_HARD -> generateHard()
      DIFF_EXPERT -> generateExpert()
      DIFF_NIGHTMARE -> generateNightmare()
      else -> generateMedium()
    }
  }

  private fun generateEasy(): MathProblem {
    val isAddition = Random.nextBoolean()
    val question: String
    val answer: Int

    if (isAddition) {
      val a = Random.nextInt(3, 25)
      val b = Random.nextInt(3, 20)
      question = "$a + $b = ?"
      answer = a + b
    } else {
      val a = Random.nextInt(12, 35)
      val b = Random.nextInt(3, a - 2)
      question = "$a - $b = ?"
      answer = a - b
    }

    val options = createOptions(answer, deltaRange = 1..6)
    return MathProblem(question, answer, options, DIFF_EASY)
  }

  private fun generateMedium(): MathProblem {
    val type = Random.nextInt(3)
    val question: String
    val answer: Int

    when (type) {
      0 -> { // 2-digit addition
        val a = Random.nextInt(18, 65)
        val b = Random.nextInt(15, 55)
        question = "$a + $b = ?"
        answer = a + b
      }
      1 -> { // 2-digit subtraction
        val a = Random.nextInt(45, 99)
        val b = Random.nextInt(18, a - 5)
        question = "$a - $b = ?"
        answer = a - b
      }
      else -> { // multiplication table
        val a = Random.nextInt(6, 12)
        val b = Random.nextInt(4, 9)
        question = "$a × $b = ?"
        answer = a * b
      }
    }

    val options = createOptions(answer, deltaRange = 1..12)
    return MathProblem(question, answer, options, DIFF_MEDIUM)
  }

  private fun generateHard(): MathProblem {
    val type = Random.nextInt(3)
    val question: String
    val answer: Int

    when (type) {
      0 -> { // 2-digit multiplication
        val a = Random.nextInt(11, 18)
        val b = Random.nextInt(11, 17)
        question = "$a × $b = ?"
        answer = a * b
      }
      1 -> { // (A + B) × C
        val a = Random.nextInt(7, 18)
        val b = Random.nextInt(6, 16)
        val c = Random.nextInt(3, 7)
        question = "($a + $b) × $c = ?"
        answer = (a + b) * c
      }
      else -> { // Division & addition
        val divisor = Random.nextInt(4, 9)
        val quotient = Random.nextInt(7, 18)
        val dividend = divisor * quotient
        val add = Random.nextInt(15, 45)
        question = "$dividend ÷ $divisor + $add = ?"
        answer = quotient + add
      }
    }

    val options = createOptions(answer, deltaRange = 2..20)
    return MathProblem(question, answer, options, DIFF_HARD)
  }

  private fun generateExpert(): MathProblem {
    // EXPERT: 2-digit × 2-digit mixed with 3-digit subtraction, square numbers, or multi-step parenthesis
    val type = Random.nextInt(3)
    val question: String
    val answer: Int

    when (type) {
      0 -> { // A × B - C
        val a = Random.nextInt(17, 36)
        val b = Random.nextInt(14, 29)
        val product = a * b
        val c = Random.nextInt(85, product - 40)
        question = "$a × $b - $c = ?"
        answer = product - c
      }
      1 -> { // A^2 + B × C
        val base = Random.nextInt(13, 22) // 13^2=169 to 21^2=441
        val square = base * base
        val b = Random.nextInt(8, 16)
        val c = Random.nextInt(11, 19)
        val prod = b * c
        question = "$base² + $b × $c = ?"
        answer = square + prod
      }
      else -> { // (A + B) × C - D
        val a = Random.nextInt(24, 68)
        val b = Random.nextInt(18, 55)
        val c = Random.nextInt(6, 11)
        val sub = Random.nextInt(60, 220)
        val sum = a + b
        question = "($a + $b) × $c - $sub = ?"
        answer = sum * c - sub
      }
    }

    val options = createOptions(answer, deltaRange = 5..45)
    return MathProblem(question, answer, options, DIFF_EXPERT)
  }

  private fun generateNightmare(): MathProblem {
    // NIGHTMARE: Real mental shockers!
    // Sum of two 2-digit products (e.g. 34 × 27 + 18 × 43),
    // or Product minus Product, or 3-digit multiplication with division
    val type = Random.nextInt(3)
    val question: String
    val answer: Int

    when (type) {
      0 -> { // A × B + C × D
        val a = Random.nextInt(23, 48)
        val b = Random.nextInt(17, 39)
        val c = Random.nextInt(16, 42)
        val d = Random.nextInt(15, 38)
        question = "$a × $b + $c × $d = ?"
        answer = (a * b) + (c * d)
      }
      1 -> { // A^2 - B^2 + C × D
        val a = Random.nextInt(22, 35)
        val b = Random.nextInt(12, a - 2)
        val c = Random.nextInt(14, 28)
        val d = Random.nextInt(9, 18)
        val diffOfSquares = (a * a) - (b * b)
        val prod = c * d
        question = "$a² - $b² + $c × $d = ?"
        answer = diffOfSquares + prod
      }
      else -> { // (A × B - C) ÷ D + E
        val d = Random.nextInt(6, 14)
        val quotient = Random.nextInt(25, 75)
        val numerator = d * quotient
        val c = Random.nextInt(80, 280)
        val product = numerator + c
        // pick a, b factors
        val a = Random.nextInt(18, 38)
        val b = product / a
        val adjustedProduct = a * b
        val adjustedNumerator = adjustedProduct - c
        val actualQuotient = adjustedNumerator / d
        val e = Random.nextInt(35, 120)
        question = "($a × $b - $c) ÷ $d + $e = ?"
        answer = actualQuotient + e
      }
    }

    val options = createOptions(answer, deltaRange = 8..95)
    return MathProblem(question, answer, options, DIFF_NIGHTMARE)
  }

  private fun createOptions(correctAnswer: Int, deltaRange: IntRange): List<Int> {
    val options = mutableSetOf(correctAnswer)
    val deltas = mutableListOf<Int>()
    for (i in 1..8) {
      val d = Random.nextInt(deltaRange.first, deltaRange.last + 1)
      deltas.add(d)
      deltas.add(-d)
    }
    deltas.addAll(listOf(1, -1, 10, -10, 20, -20, 100, -100))
    deltas.shuffle()

    for (delta in deltas) {
      val candidate = correctAnswer + delta
      if (candidate != correctAnswer && candidate >= 0) {
        options.add(candidate)
      }
      if (options.size == 4) break
    }

    var step = 1
    while (options.size < 4) {
      val candidate = correctAnswer + (if (step % 2 == 0) step * 3 else -step * 3)
      if (candidate >= 0) {
        options.add(candidate)
      }
      step++
    }

    return options.toList().shuffled()
  }
}
