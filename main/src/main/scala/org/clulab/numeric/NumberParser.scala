package org.clulab.numeric

import scala.collection.mutable.ArrayBuffer

/**
  * Parses textual numbers, e.g., "twelve hundred", into numbers, e.g., "1200"
  */
object NumberParser {

  def parse(words: Seq[String]): Option[Double] = {
    words match {
      case Seq() => None
      case words =>
	    // remove 's' from words like "thousands"
	    val cleanWords = words.map { w =>
		  if (w.endsWith("s")) w.dropRight(1) else w
	    }
	    parseWords(cleanWords) orElse parseNumeric(cleanWords)
    }
  }

  def parseNumeric(words: Seq[String]): Option[Double] = {
    try {
      var number: Double = 1
      var numerator: Option[Double] = None
      for (w <- words) {
        w match {
          case "-" =>
            number *= -1
          case "/" =>
            numerator = Some(number)
            number = 1
          case w if w.contains("/") =>
            val Array(w1, w2) = w.split("/")
            number *= w1.toDouble
            numerator = Some(number)
            number = w2.toDouble
          case w if americanNumberSystem.contains(w) =>
            number *= americanNumberSystem(w)
          case w =>
            number *= w.toDouble
        }
      }
      numerator match {
        case None => Some(number)
        case Some(n) => Some(n / number)
      }
    } catch {
      case _: Exception => None
    }
  }

  def parseWords(words: Seq[String]): Option[Double] = {
    // if single token then return corresponding number
    if (words.length == 1) {
      return americanNumberSystem.get(words.head)
    }
    try {
      // accumulate result here
      var totalSum: Double = 0
      var remainingWords = words.toArray
      for (w <- Seq("quadrillion", "trillion", "billion", "million", "thousand")) {
        val index = remainingWords.indexOf(w)
        if (index >= 0) {
          val multiplier = numberFormation(remainingWords.slice(0, index))
          remainingWords = remainingWords.drop(index + 1)
          totalSum += multiplier * americanNumberSystem(w)
        }
      }
      // handle hundreds
      totalSum += numberFormation(remainingWords)
      // return number
      Some(totalSum)
    } catch {
      case _: Exception => None
    }
  }

  def numberFormation(words: Array[String]): Double = {
    val numbers = ArrayBuffer.empty[Double]
    for (w <- words) {
       numbers += americanNumberSystem(w)
    }
    numbers.size match {
      case 4 => numbers(0) * numbers(1) + numbers(2) + numbers(3)
      case 3 => numbers(0) * numbers(1) + numbers(2)
      case 2 if numbers.contains(100) => numbers(0) * numbers(1)
      case 2 => numbers(0) + numbers(1)
      case 1 => numbers(0)
      case 0 => 0
    }
  }

  // https://en.wikipedia.org/wiki/Names_of_large_numbers
  val americanNumberSystem: Map[String, Double] = Map[String, Double](
    "zero"        -> 0,
    "one"         -> 1,
    "two"         -> 2,
    "three"       -> 3,
    "four"        -> 4,
    "five"        -> 5,
    "six"         -> 6,
    "seven"       -> 7,
    "eight"       -> 8,
    "nine"        -> 9,
    "ten"         -> 10,
    "eleven"      -> 11,
    "twelve"      -> 12,
    "thirteen"    -> 13,
    "fourteen"    -> 14,
    "fifteen"     -> 15,
    "sixteen"     -> 16,
    "seventeen"   -> 17,
    "eighteen"    -> 18,
    "nineteen"    -> 19,
    "twenty"      -> 20,
    "thirty"      -> 30,
    "forty"       -> 40,
    "fifty"       -> 50,
    "sixty"       -> 60,
    "seventy"     -> 70,
    "eighty"      -> 80,
    "ninety"      -> 90,
    "hundred"     -> 1e2,
    "thousand"    -> 1e3,
    "million"     -> 1e6,
    "billion"     -> 1e9,
    "trillion"    -> 1e12,
    "quadrillion" -> 1e15
  )

}
