package org.clulab.numeric

import org.clulab.processors.Sentence
import org.clulab.processors.clu.BalaurProcessor
import org.clulab.processors.clu.tokenizer.Tokenizer
import org.clulab.struct.Interval
import org.clulab.utils.{Test, Timer}
import org.scalatest.concurrent.TimeLimits
import org.scalatest.time.{Seconds, Span}

import scala.util.matching.Regex

class TestNumericEntityRecognition extends Test {

  class HabitusTokenizer(tokenizer: Tokenizer) extends Tokenizer(tokenizer.lexer, tokenizer.steps, tokenizer.sentenceSplitter) {
    // TODO: Make sure en dash is preserved in raw somehow!

    override def tokenize(text: String, sentenceSplit: Boolean = true, characterOffset: Int): Seq[Sentence] = {
      // Cheat and swap out some en dashes if necessary.
      val habitusText =
        if (text.contains(HabitusTokenizer.endash))
          HabitusTokenizer.regex.replaceAllIn(text,HabitusTokenizer.replacer)
        else
          text

      tokenizer.tokenize(habitusText, sentenceSplit, characterOffset)
    }
  }

  object HabitusTokenizer {
    val dash = "-"
    val endash = "\u2013"
    val regex = {
      val start = "^"
      val end = "$"
      val notDigit = "[^\\d]"
      val exponent = "[12]" // So far 1 and are all we've encountered as exponents.

      s"($start|$notDigit)$endash($exponent($notDigit|$end))".r
    }
    val replacer: Regex.Match => String = m => s"${m.group(1)}$dash${m.group(2)}"
  }

  class HabitusProcessor() extends BalaurProcessor {
    lazy val habitusTokenizer: HabitusTokenizer = new HabitusTokenizer(super.tokenizer)
    override def tokenizer: Tokenizer = habitusTokenizer
  }

  val ner = NumericEntityRecognizer()
  val proc = new HabitusProcessor()

  //
  // unit tests starts here
  //

  // these should be captured by rules date-1 and date-2
  "the numeric entity recognizer" should "recognize dates in the European format" in {
    ensure("It is 12 May, 2000", Interval(2, 6), "DATE", "2000-05-12")
    ensure("It was May 2000", Interval(2, 4), "DATE", "2000-05-XX")
    ensure("It was 25 May", Interval(2, 4), "DATE", "XXXX-05-25")
    ensure("It was May", Interval(2, 3), "DATE", "XXXX-05-XX")
  }

  // these should be captured by rules date-3 and date-4
   it should "recognize dates in the American format" in {
     ensure("It is 2000, May 12", Interval(2, 6), "DATE", "2000-05-12")
     ensure("It was May 31", Interval(2, 4), "DATE", "XXXX-05-31")
     ensure("It was 2000", Interval(2,3), "DATE", "2000-XX-XX")
     ensure("It was 2000, May", Interval(2, 5), "DATE", "2000-05-XX")
   }

  // this is to ensure the measurement-3 rule does not capture a preceding year as a value that shares a unit with another value (as in "yields were set to 6.4, 7.9, and 7.1 t/ha")
  it should "not include a year as as a conjoined value" in {
    ensure("average yield reached 72 t ha-1 in 1999 and 82 t ha-1 in 2000", Interval(7, 8), "DATE", "1999-XX-XX")
  }

  it should "recognize numeric dates" in {
    // these should be captured by rule date-yyyy-mm-dd
    ensure("It is 2000:05:12", Interval(2, 3), "DATE", "2000-05-12")
    ensure("It is 2000/05/12", Interval(2, 3), "DATE", "2000-05-12")
    ensure("It is 2000-05-12", Interval(2, 3), "DATE", "2000-05-12")

    // these should be captured by rule date-dd-mm-yyyy
    ensure("It is 12/05/2000", Interval(2, 3), "DATE", "2000-05-12")
    ensure("It is 12:05:2000", Interval(2, 3), "DATE", "2000-05-12")
    ensure("It is 12-05-2000", Interval(2, 3), "DATE", "2000-05-12")
  }

  it should "recognize numeric dates 2" in {
    // these tests should be captured by yyyy-mm-dd
    ensure(sentence= "ISO date is 1988-02-17.", Interval(3, 4), goldEntity= "DATE", goldNorm= "1988-02-17")
    ensure(sentence= "1988-02-17.", Interval(0, 1), goldEntity= "DATE", goldNorm= "1988-02-17")
    ensure(sentence= "1988/02/17.", Interval(0, 1), goldEntity= "DATE", goldNorm= "1988-02-17")

    // Any confusion between European and American date format. We go with American one.
    ensure(sentence= "ISO date is 1988-02-03.", Interval(3, 4), goldEntity= "DATE", goldNorm= "1988-02-03")
    ensure(sentence= "ISO date is 1988/02/03.", Interval(3, 4), goldEntity= "DATE", goldNorm= "1988-02-03")
    ensure(sentence= "1988/02/03.", Interval(0, 1), goldEntity= "DATE", goldNorm= "1988-02-03")
  }

  it should "recognize numeric dates of form yy-mm-dd" in  {
    ensure(sentence= "88/02/15.", Interval(0, 1), goldEntity= "DATE", goldNorm= "XX88-02-15")
    ensure(sentence= "ISO date is 88/02/15.", Interval(3, 4), goldEntity= "DATE", goldNorm= "XX88-02-15")
  }

  it should "recognize numeric dates of form mm-yyyy" in  {
    // These tests should be captured by rule mm-yyyy
    ensure(sentence= "02-1988.", Interval(0, 1), goldEntity= "DATE", goldNorm= "1988-02-XX")
    ensure(sentence= "ISO date is 02/1988.", Interval(3, 4), goldEntity= "DATE", goldNorm= "1988-02-XX")
    ensure(sentence= "02/1988.", Interval(0, 1), goldEntity= "DATE", goldNorm= "1988-02-XX")
    ensure(sentence= "ISO date is 02/1988.", Interval(3, 4), goldEntity= "DATE", goldNorm= "1988-02-XX")
    ensure(sentence= "02/1988.", Interval(0, 1), goldEntity= "DATE", goldNorm= "1988-02-XX")
  }

  it should "recognize numeric dates of form yyyy-mm" in {
    // These tests are captured by rule yyyy-mm
    ensure(sentence= "ISO date is 1988-02.", Interval(3, 4), goldEntity= "DATE", goldNorm= "1988-02-XX")
    ensure(sentence= "1988-02.", Interval(0, 1), goldEntity= "DATE", goldNorm= "1988-02-XX")
    ensure(sentence= "ISO date is 1988/02.", Interval(3, 4), goldEntity= "DATE", goldNorm= "1988-02-XX")
    ensure(sentence= "1988/02.", Interval(0, 1), goldEntity= "DATE", goldNorm= "1988-02-XX")
  }

  it should "recognize intensive SUTimes tests without a year" in {
    ensure(sentence= "Sun Apr 21", Interval(1,3), goldEntity= "DATE", goldNorm= "XXXX-04-21")
    ensure(sentence= "Sun Apr 24", Interval(1,3), goldEntity= "DATE", goldNorm= "XXXX-04-24")
    ensure(sentence= "Sun Apr 26", Interval(1,3), goldEntity= "DATE", goldNorm= "XXXX-04-26")
    ensure(sentence= "Wed May 1", Interval(1,3), goldEntity= "DATE", goldNorm= "XXXX-05-01")
    ensure(sentence= "Wed May 3", Interval(1,3), goldEntity= "DATE", goldNorm= "XXXX-05-03")
    ensure(sentence= "Wed May 5", Interval(1,3), goldEntity= "DATE", goldNorm= "XXXX-05-05")
    ensure(sentence= "Wed May 10", Interval(1,3), goldEntity= "DATE", goldNorm= "XXXX-05-10")
    ensure(sentence= "Fri May 11", Interval(1,3), goldEntity= "DATE", goldNorm= "XXXX-05-11")
    ensure(sentence= "Mon May 15", Interval(1,3), goldEntity= "DATE", goldNorm= "XXXX-05-15")
    ensure(sentence= "Wed May 18", Interval(1,3), goldEntity= "DATE", goldNorm= "XXXX-05-18")
    ensure(sentence= "Thur May 22", Interval(1,3), goldEntity= "DATE", goldNorm= "XXXX-05-22")
    ensure(sentence= "Mon May 27", Interval(1,3), goldEntity= "DATE", goldNorm= "XXXX-05-27")
    ensure(sentence= "Tue May 31", Interval(1,3), goldEntity= "DATE", goldNorm= "XXXX-05-31")
    ensure(sentence= "Mon Jun 3", Interval(1,3), goldEntity= "DATE", goldNorm= "XXXX-06-03")
    ensure(sentence= "Jun 8", Interval(0,2), goldEntity= "DATE", goldNorm= "XXXX-06-08")
    ensure(sentence= "Jun 18", Interval(0,2), goldEntity= "DATE", goldNorm= "XXXX-06-18")
    ensure(sentence= "Jun 18 2018", Interval(0,2), goldEntity= "DATE", goldNorm= "2018-06-18")
    ensure(sentence= "2018 Jun 18", Interval(0,2), goldEntity= "DATE", goldNorm= "2018-06-18")
  }

  it should "recognize numeric SUTimes tests" in {
    ensure(sentence= "2010-11-15", Interval(0,1), goldEntity= "DATE", goldNorm= "2010-11-15")
    ensure(sentence= "2010-11-16", Interval(0,1), goldEntity= "DATE", goldNorm= "2010-11-16")
    ensure(sentence= "2010-11-17", Interval(0,1), goldEntity= "DATE", goldNorm= "2010-11-17")
    ensure(sentence= "2010/11/18", Interval(0,1), goldEntity= "DATE", goldNorm= "2010-11-18")
    //TODO "1988-SP", "1988-SU", "1988-FA", "1988-FA", "1988-WI" we can extend this to capture this or SV cropping seasons
    //TODO "1988-02", "1988-Q2" needs Mihai approval
    ensure(sentence= "Cropping season starts on 2010-07", Interval(4,5), goldEntity= "DATE", goldNorm= "2010-07-XX")
    ensure(sentence= "It is 2010-08", Interval(2,3), goldEntity= "DATE", goldNorm= "2010-08-XX")
    ensure(sentence= "2010-10", Interval(0,1), goldEntity= "DATE", goldNorm= "2010-10-XX")
    ensure(sentence= "2010-12", Interval(0,1), goldEntity= "DATE", goldNorm= "2010-12-XX")
  }

  it should "recognize numeric dates of form yy-mm" in {
    ensure(sentence= "19:02.", Interval(0, 1), goldEntity= "DATE", goldNorm= "XX19-02-XX")
  }

  it should "recognize one token year ranges" in {
    ensure(sentence= "2021/2022", Interval(0, 1), goldEntity= "DATE-RANGE", goldNorm= "2021-XX-XX -- 2022-XX-XX")
    ensure(sentence= "2000-2009", Interval(0, 1), goldEntity= "DATE-RANGE", goldNorm= "2000-XX-XX -- 2009-XX-XX")
  }

  it should "recognize numeric dates of form month of year" in {
    ensure(sentence= "sowing date is best in May of 2020", Interval(5, 8), goldEntity= "DATE", goldNorm= "2020-05-XX")
    ensure(sentence= "sowing date in July of 2020", Interval(3, 6), goldEntity= "DATE", goldNorm= "2020-07-XX")
    ensure(sentence= "It is not desirable to sow in January of 2001", Interval(7, 10), goldEntity= "DATE", goldNorm= "2001-01-XX")
    ensure(sentence= "It is not desirable to sow in Jan of 2001", Interval(7, 10), goldEntity= "DATE", goldNorm= "2001-01-XX")
    ensure(sentence= "It is not desirable to sow in Jul of 2001", Interval(7, 10), goldEntity= "DATE", goldNorm= "2001-07-XX")
    ensure(sentence= "It is not desirable to sow in Aug of 2001", Interval(7, 10), goldEntity= "DATE", goldNorm= "2001-08-XX")
    ensure(sentence= "It is not desirable to sow in Dec of 2001", Interval(7, 10), goldEntity= "DATE", goldNorm= "2001-12-XX")
  }

  it should "recognize numeric dates of form month date of year" in {
    ensure(sentence= "sowing date is best on October 8 of 2020", Interval(5, 9), goldEntity= "DATE", goldNorm= "2020-10-08")
    ensure(sentence= "Rain will be on April 8 of 2020", Interval(4, 8), goldEntity= "DATE", goldNorm= "2020-04-08")
    ensure(sentence= "December 18 of 2002", Interval(0, 4), goldEntity= "DATE", goldNorm= "2002-12-18")
    ensure(sentence= "February 21 of 1002", Interval(0, 4), goldEntity= "DATE", goldNorm= "1002-02-21")
  }

  it should "recognize numeric dates of form month date in year" in {
    ensure("The first sowing dates started on July 1st in 2010 and on July 8th in 2011", Interval(6, 10), "DATE", "2010-07-01")
    ensure("The first sowing dates started on July 1st in 2010 and on July 8th in 2011", Interval(12, 16), "DATE", "2011-07-08")
  }

  it should "recognize dates with ordinal days" in {
    ensure(sentence = "Planting dates are between July 1st and August 2nd.", Interval(3, 9), goldEntity = "DATE-RANGE", "XXXX-07-01 -- XXXX-08-02")
  }

  it should "recognize \"month of X\" patterns" in {
    ensure(sentence = "It was the month of January", Interval(3, 6), goldEntity = "DATE", goldNorm = "XXXX-01-XX")
    ensure(sentence = "It was the month of January, 2020", Interval(3, 8), goldEntity = "DATE", goldNorm = "2020-01-XX")
  }

  it should "recognize date from holiday" in {
    ensure(sentence= "Christmas 2016", Interval(0, 2), goldEntity= "DATE", goldNorm= "2016-12-25")
    ensure(sentence= "Independence Day", Interval(0, 2), goldEntity= "DATE", goldNorm= "XXXX-07-04")
    ensure(sentence= "independence day", Interval(0, 2), goldEntity= "DATE", goldNorm= "XXXX-07-04")
    ensure(sentence= "New Years Eve", Interval(0, 2), goldEntity= "DATE", goldNorm= "XXXX-12-31")
    ensure(sentence= "new year's eve", Interval(0, 2), goldEntity= "DATE", goldNorm= "XXXX-12-31")
    ensure(sentence= "Martin Luther King Jr. Day 2022", Interval(0, 4), goldEntity= "DATE", goldNorm= "2022-01-17")
    ensure(sentence= "MLK day 2022", Interval(0, 1), goldEntity= "DATE", goldNorm= "2022-01-17")
    ensure(sentence= "before Patriots' day 2021", Interval(0, 2), goldEntity= "DATE-RANGE", goldNorm= "XXXX-XX-XX -- 2021-04-19")
    ensure(sentence= "between Christmas and New Year", Interval(0, 4), goldEntity= "DATE-RANGE", goldNorm= "XXXX-12-25 -- XXXX-01-01")
  }

  // TODO: We need a parser for dot dates separated
  it should "recognize Numerical dates with dot separated" in {
    // ensure("on 15.07.2016", Interval(0, 2), "DATE", "2016-07-15")
    // ensure("on 07.2016", Interval(0, 2), "DATE", "2016-07-XX")
    // ensure("on 15.07", Interval(0, 2), "DATE", "XXXX-07-15")
    // ensure("Sowing depended on the available soil moisture and was done on 15.07.2016", Interval(10, 12), "DATE", "2016-07-15")
    // ensure("resulting in harvest in October or November", Interval(4, 7), "DATE", "XXXX-11-XX")
  }

  it should "recognize literal date ranges" in {
    ensure("between 2020/10/10 and 2020/11/11", Interval(0, 4), "DATE-RANGE", "2020-10-10 -- 2020-11-11")
    ensure("from July 20 to July 31", Interval(0, 6), "DATE-RANGE", "XXXX-07-20 -- XXXX-07-31")
    ensure("from 20 to July 31", Interval(0, 5), "DATE-RANGE", "XXXX-07-20 -- XXXX-07-31")
    ensure("between October 31 and December 31 of 2020", Interval(0, 8), "DATE-RANGE", "2020-10-31 -- 2020-12-31")
    ensure("Sowing between October 31 and December 30 , 2020 is optimal", Interval(1, 9), "DATE-RANGE", "2020-10-31 -- 2020-12-30")
  }

  it should "recognize date month ranges" in {
    ensure("between January and June 2017", Interval(0, 5), "DATE-RANGE", "2017-01-XX -- 2017-06-XX")
    ensure("March to May 2017", Interval(0, 4), "DATE-RANGE", "2017-03-XX -- 2017-05-XX")
    ensure("May - October 2016", Interval(0, 4), "DATE-RANGE", "2016-05-XX -- 2016-10-XX")
  }

  it should "recognize different dates" in {
    ensure("January 2016 and June 2017", Interval(0, 2), "DATE", "2016-01-XX")
    ensure("January 2016 and June 2017", Interval(3, 5), "DATE", "2017-06-XX")
  }

  it should "recognize relative dates" in {
    ensure("Since January 2016", Interval(0, 3), "DATE-RANGE", "2016-01-XX -- ref-date")
    ensure("Until January 2016", Interval(0, 3), "DATE-RANGE", "ref-date -- 2016-01-XX")
    ensure("from January 2016", Interval(0, 3), "DATE-RANGE", "2016-01-XX -- ref-date")
  }

  it should "recognize unbounded date ranges" in {
    ensure("before July 2016", Interval(0, 3), "DATE-RANGE", "XXXX-XX-XX -- 2016-07-XX")
    ensure("prior to July 2016", Interval(0, 4), "DATE-RANGE", "XXXX-XX-XX -- 2016-07-XX")
    ensure("after July 2016", Interval(0, 3), "DATE-RANGE", "2016-07-XX -- XXXX-XX-XX")
    ensure("before July 15", Interval(0, 3), "DATE-RANGE", "XXXX-XX-XX -- XXXX-07-15")
    ensure("after July 15", Interval(0, 3), "DATE-RANGE", "XXXX-07-15 -- XXXX-XX-XX")
  }

  it should "recognize date ranges from seasons" in {
    ensure("winter 2017", Interval(0, 2), "DATE-RANGE", "2016-12-21 -- 2017-03-20")
    ensure("spring 2017", Interval(0, 2), "DATE-RANGE", "2017-03-20 -- 2017-06-21")
    ensure("summer 2017", Interval(0, 2), "DATE-RANGE", "2017-06-21 -- 2017-09-22")
    ensure("autumn 2017", Interval(0, 2), "DATE-RANGE", "2017-09-22 -- 2017-12-21")
    ensure("autumn in 2017", Interval(0, 3), "DATE-RANGE", "2017-09-22 -- 2017-12-21")
    ensure("2017 autumn", Interval(0, 2), "DATE-RANGE", "2017-09-22 -- 2017-12-21")
    ensure("winter", Interval(0, 1), "DATE-RANGE", "XXXX-12-21 -- XXXX-03-20")
    ensure("autumn", Interval(0, 1), "DATE-RANGE", "XXXX-09-22 -- XXXX-12-21")
//    ensure("spring", Interval(0, 1), "DATE-RANGE", "XXXX-03-20 -- XXXX-06-21") // alice: failing this test is an expected behavior as raw spring/fall is now filtered out by postprocessNumericEntities (filtering out homonyms of spring/falls)
    ensure("fall 2021", Interval(0, 2), "DATE-RANGE", "2021-09-22 -- 2021-12-21")
    ensure("in the fall", Interval(2, 3), "DATE-RANGE", "XXXX-09-22 -- XXXX-12-21")
    ensure("fall", Interval(0, 1), "", "")
    ensure("spring", Interval(0, 1), "", "")
  }

  it should "recognize date ranges with seasons" in {
    ensure("from spring to autumn 2017", Interval(0, 4), "DATE-RANGE", "2017-06-21 -- 2017-09-22")
    ensure("between spring and autumn 2017", Interval(0, 4), "DATE-RANGE", "2017-06-21 -- 2017-09-22")
    ensure("between autumn 2017 and spring 2018", Interval(0, 5), "DATE-RANGE", "2017-12-21 -- 2018-03-20")
    ensure("between autumn and spring", Interval(0, 4), "DATE-RANGE", "XXXX-12-21 -- XXXX-03-20")
    ensure("Since winter 2017", Interval(0, 3), "DATE-RANGE", "2017-03-20 -- ref-date")
    ensure("Since winter", Interval(0, 2), "DATE-RANGE", "XXXX-03-20 -- ref-date")
    ensure("Until summer 2017", Interval(0, 3), "DATE-RANGE", "ref-date -- 2017-06-21")
    ensure("Until summer", Interval(0, 2), "DATE-RANGE", "ref-date -- XXXX-06-21")
  }

  it should "recognize unbounded date ranges with seasons" in {
    ensure("before summer 2016", Interval(0, 3), "DATE-RANGE", "XXXX-XX-XX -- 2016-06-21")
    ensure("prior to summer 2016", Interval(0, 4), "DATE-RANGE", "XXXX-XX-XX -- 2016-06-21")
    ensure("after summer 2016", Interval(0, 3), "DATE-RANGE", "2016-09-22 -- XXXX-XX-XX")
    ensure("before summer", Interval(0, 2), "DATE-RANGE", "XXXX-XX-XX -- XXXX-06-21")
    ensure("after summer", Interval(0, 2), "DATE-RANGE", "XXXX-09-22 -- XXXX-XX-XX")
  }

  it should "recognize dates and date-ranges with single months" in {
    ensure("It was January", Interval(2, 3), "DATE", "XXXX-01-XX")
    ensure("It was Jan", Interval(2, 3), "DATE", "XXXX-01-XX")
    ensure("It was between January and March", Interval(2, 3), "DATE-RANGE", "XXXX-01-XX -- XXXX-03-XX")
    ensure("until January", Interval(0, 2), "DATE-RANGE", "ref-date -- XXXX-01-XX")
    ensure("after January", Interval(0, 2), "DATE-RANGE", "XXXX-01-XX -- XXXX-XX-XX")
  }

  it should "recognize dates and date-ranges with modifier" in {
    ensure(sentence= "It was the start of 2020", Interval(2, 6), goldEntity= "DATE", goldNorm= "2020-01-XX")
    ensure(sentence= "It was mid-2020", Interval(2, 4), goldEntity= "DATE", goldNorm= "2020-06-XX")
    ensure(sentence= "It was the end of 2020", Interval(2, 6), goldEntity= "DATE", goldNorm= "2020-12-XX")
    ensure(sentence= "It was the start of January", Interval(2, 6), goldEntity= "DATE", goldNorm= "XXXX-01-01")
    ensure(sentence= "It was mid-January", Interval(2, 4), goldEntity= "DATE", goldNorm= "XXXX-01-15")
    ensure(sentence= "It was the end of January", Interval(2, 6), goldEntity= "DATE", goldNorm= "XXXX-01-31")
    ensure(sentence= "It was the end of February 2020", Interval(2, 7), goldEntity= "DATE", goldNorm= "2020-02-29")
    ensure(sentence= "It was around January 15", Interval(2, 4), goldEntity= "DATE", goldNorm= "XXXX-01-15 [APPROX]")
    ensure(sentence= "It was around New Years Eve", Interval(2, 4), goldEntity= "DATE", goldNorm= "XXXX-12-31 [APPROX]")
    ensure(sentence= "It was around mid-January", Interval(2, 4), goldEntity= "DATE", goldNorm= "XXXX-01-15 [APPROX]")
    ensure(sentence= "It was around the start of 2020", Interval(2, 7), goldEntity= "DATE", goldNorm= "2020-01-XX [APPROX]")
    ensure(sentence= "before the end of 2020", Interval(0, 5), goldEntity= "DATE-RANGE", goldNorm= "XXXX-XX-XX -- 2020-12-XX")
    ensure(sentence= "from early June to mid-September", Interval(0, 6), goldEntity= "DATE-RANGE", goldNorm= "XXXX-06-01 -- XXXX-09-15")
    ensure(sentence= "since around the end of 2020", Interval(0, 6), goldEntity= "DATE-RANGE", goldNorm= "2020-12-XX [APPROX] -- ref-date")
  }

  // TODO: Happenings in the middle of months

  it should "recognize dates as the middle part of months" in {
    // ensure("planting from mid-February", Interval(2, 5), "DATE", "XXXX-02-15")
    // ensure("planting from mid-March", Interval(2, 5), "DATE", "XXXX-03-15")
    // ensure("As a function of the onset of rains, rice was sown mid-July in 2016 and early July in 2017", Interval(2, 5), "DATE", "XXXX-03-15")
    // ensure("sowing normally occurs in summer mid-June", Interval(5, 8), "DATE-RANGE",  "XXXX-10-25 -- XXXX-12-10")
  }

  //TODO: Additional mixed dates ranges 

  it should "recognize date ranges" in {
    // ensure("to harvesting in June-July", Interval(3, 6), "DATE-RANGE", "XXXX-06-XX -- XXXX-07-XX")
    // // ensure("planting from mid-February and mid-March", Interval(3, 9), "DATE-RANGE",  "XXXX-02-14 -- XXXX-03-14")
    // ensure("harvesting from October through December", Interval(1, 5), "DATE-RANGE",  "XXXX-10-XX -- XXXX-12-XX")
    ensure("sowing from 25th Oct to 10th Dec", Interval(1, 7), "DATE-RANGE",  "XXXX-10-25 -- XXXX-12-10")
    // ensure("rainfall pattern from June to mid-September", Interval(2, 8), "DATE-RANGE",  "XXXX-06-XX -- XXXX-09-15")
    // ensure("when heading occurred between August 10 and 25", Interval(3, 8), "DATE-RANGE",  "XXXX-08-10 -- XXXX-08-25")
    // ensure("drier season between November and March", Interval(2, 8), "DATE-RANGE",  "XXXX-11-XX -- XXXX-03-XX")
    // ensure("flooding are expected to occur in July to August 2021", Interval(5, 10), "DATE-RANGE",  "2021-07-XX -- 2021-08-XX")
    ensure("farmers sowed Jaya between 20 June and 1 July", Interval(3, 8), "DATE-RANGE",  "XXXX-06-20 -- XXXX-07-01")
    ensure(sentence= "transplanted during the 1st week of July", Interval(3, 7), goldEntity= "DATE-RANGE", goldNorm= "XXXX-07-01 -- XXXX-07-07")
    ensure(sentence= "We planted corn the first two weeks of April.", Interval(4, 9), goldEntity= "DATE-RANGE", goldNorm= "XXXX-04-01 -- XXXX-04-14")
    ensure(sentence= "We planted beans the second week of May.", Interval(4, 8), goldEntity= "DATE-RANGE", goldNorm= "XXXX-05-08 -- XXXX-05-14")
    ensure(sentence= "We planted beans in the last week of June.", Interval(5, 9), goldEntity= "DATE-RANGE", goldNorm= "XXXX-06-24 -- XXXX-06-30")
    ensure(sentence= "We planted beans in the last two weeks of February.", Interval(5, 10), goldEntity= "DATE-RANGE", goldNorm= "XXXX-02-15 -- XXXX-02-28")
  }

  it should "recognize weird date ranges" in {
    ensure("These correspond to the dry season (from February/March to June/July)",
      Interval(7, 15), "DATE-RANGE", "XXXX-02-XX -- XXXX-07-XX")
  }

  it should "recognize date ranges with vague seasons" in {
    ensure("Seeding dates ranged from 22 August to 26 September in 2011WS.",
      Interval(3, 11), "DATE-RANGE", "2011-08-22 -- 2011-09-26")
    ensure("The planned timing for the first split was 23 days after sowing (from 3 to 13 August in the 1999WS and from 14 to 25 August in the 2000WS",
      Interval(13, 21), "DATE-RANGE", "1999-08-03 -- 1999-08-13"
    )
    ensure("The planned timing for the first split was 23 days after sowing (from 3 to 13 August in the 1999WS and from 14 to 25 August in the 2000WS",
      Interval(22, 29), "DATE-RANGE", "2000-08-14 -- 2000-08-25"
    )
  }

  it should "recognize date ranges (month/day) with vague seasons" in {
    ensure("from August 23 to October 11 in 2017WS.",
      Interval(0, 8), "DATE-RANGE", "2017-08-23 -- 2017-10-11")
  }

  it should "recognize years with vague seasons within same token as date ranges" in {
    ensure("Timing of basal fertilizer application was on average 26 days after sowing in 2011WS",
      Interval(13, 14), "DATE-RANGE", "2011-XX-XX -- 2011-XX-XX")
  }

  it should "recognize years with vague seasons (DS) within same token as date ranges" in {
    ensure("Timing of basal fertilizer application was on average 26 days after sowing in 2015DS",
      Interval(13, 14), "DATE-RANGE", "2015-XX-XX -- 2015-XX-XX")
  }

  it should "recognize years with vague seasons in separate tokens as date ranges" in {
    ensure("Timing of basal fertilizer application was on average 26 days after sowing in 2011 WS",
      Interval(13, 15), "DATE-RANGE", "2011-XX-XX -- 2011-XX-XX")
  }

  it should "recognize years with vague seasons (DS) in separate tokens as date ranges" in {
    ensure("Timing of basal fertilizer application was on average 26 days after sowing in 2019 DS",
      Interval(13, 15), "DATE-RANGE", "2019-XX-XX -- 2019-XX-XX")
  }

  // TODO: Other dates that should be recognized

  it should "recognize numeric dates of form mm" in {
    // ensure(sentence= "Rice is normally sown at the end of May", Interval(8, 9), goldEntity= "DATE", goldNorm= "XXXX-05-XX")
    // ensure(sentence= "harvested the following August", Interval(3, 4), goldEntity= "DATE", goldNorm= "XXXX-08-XX")
    // ensure(sentence= "wheat is mostly sown in late September", Interval(6, 7), goldEntity= "DATE", goldNorm= "XXXX-09-XX")
    // ensure(sentence= "Rains are expected to start in July", Interval(6, 7), goldEntity= "DATE", goldNorm= "XXXX-07-XX")
  }

  it should "recognize numeric dates of form dd-mm" in {
    // ensure(sentence= "transplanted during the 1st of July", Interval(3, 6), goldEntity= "DATE", goldNorm= "XXXX-07-01")
    // ensure(sentence= "the 20th of October", Interval(1, 4), goldEntity= "DATE", goldNorm= "XXXX-10-20")
  }

  // TODO: need to decide on the output of such dates
  it should "recognize numeric dates of form yyyy" in {
    // ensure(sentence= "the highest grain yield in 1998/99", Interval(5,7), goldEntity= "DATE-RANGE", goldNorm= "1999-XX-XX")
  }

  it should "recognize numeric dates of form mm-dd" in {
    ensure(sentence= "before Aug. 15th", Interval(0, 3), goldEntity= "DATE-RANGE", goldNorm= "XXXX-XX-XX -- XXXX-08-15")
    ensure(sentence= "after March 5th", Interval(0, 3), goldEntity= "DATE-RANGE", goldNorm= "XXXX-03-05 -- XXXX-XX-XX")
    ensure(sentence= "Farmers planted on July 11", Interval(3, 5), goldEntity= "DATE", goldNorm= "XXXX-07-11")
  }

  it should "recognize numeric dates of form mm-yy" in {
    // ensure(sentence= "July in 2016", Interval(0, 3), goldEntity= "DATE", goldNorm= "2016-07-XX")
    // ensure(sentence= "we’ll have more seed available again in Nov/Dec 2021", Interval(7, 11), goldEntity= "DATE", goldNorm= "2021-12-XX")
  }

  // TODO: We need a parser for dates of the form: dd of mm, yy or mm in yy
  it should "recognize numeric dates of form dd-mm-yy" in {
    // ensure(sentence= "SSP and potassium SOP were applied at sowing time on 24th of June, 2010", Interval(10, 15), goldEntity= "DATE", goldNorm= "2010-06-24")
    // ensure(sentence= "Jaya was planted on 14th of July 2020", Interval(4, 8), goldEntity= "DATE", goldNorm= "2000-07-14")
    ensure(sentence= "on 6th Jan, 2009", Interval(1, 5), goldEntity= "DATE", goldNorm= "2009-01-06")
    // ensure(sentence= "on 18th of Oct 2019", Interval(1, 5), goldEntity= "DATE", goldNorm= "2019-10-18")
    // ensure(sentence= "old seedlings transplanted on 14 July in 1999/00", Interval(4, 8), goldEntity= "DATE", goldNorm= "2000-07-14")
  }

  it should "recognize season in year" in {
    ensure(sentence = "We applied it in summer in 21", Interval(4, 7), goldEntity= "DATE-RANGE", goldNorm = "XX21-06-21 -- XX21-09-22")
    ensure(sentence = "We applied it in Fall in 21", Interval(4, 7), goldEntity= "DATE-RANGE", goldNorm = "XX21-09-22 -- XX21-12-21")
    ensure(sentence = "We applied it in fall of 2021", Interval(4, 7), goldEntity= "DATE-RANGE", goldNorm = "2021-09-22 -- 2021-12-21")
  }

  it should "recognize between-week date ranges" in {
    ensure("It took place between the second and third weeks of June",
      Interval(3, 11), "DATE-RANGE", "XXXX-06-08 -- XXXX-06-21")

    ensure("It took place between the first and 4th weeks of May",
      Interval(3, 11), "DATE-RANGE", "XXXX-05-01 -- XXXX-05-28")
  }
  
  it should "recognize measurement units" in {
    ensure("It was 12 ha", Interval(2, 4), "MEASUREMENT-AREA", "12.0 ha")

    // tests for unit normalization
    ensure("It was 12 hectares", Interval(2, 4), "MEASUREMENT-AREA", "12.0 ha")
    ensure(sentence= "It was 12 meters long.", Interval(2, 4), goldEntity="MEASUREMENT-LENGTH", goldNorm= "12.0 m")
    ensure(sentence= "It was 12 kilograms.", Interval(2,4), goldEntity="MEASUREMENT-MASS", goldNorm= "12.0 kg")
    ensure(sentence= "irrigated plots with a 2-5 cm depth sheet of water", Interval(4, 6), goldEntity="MEASUREMENT-LENGTH", goldNorm="2.0 -- 5.0 cm")

    // test for parsing literal numbers
    ensure("It was twelve hundred ha", Interval(2, 5), "MEASUREMENT-AREA", "1200.0 ha")
    ensure("It was 12 hundred ha", Interval(2, 5), "MEASUREMENT-AREA", "1200.0 ha")
    ensure(sentence= "Crops are 2 thousands ha wide.", Interval(2,5), goldEntity="MEASUREMENT-AREA", goldNorm= "2000.0 ha")
    ensure(sentence= "Rice crops are 1.5 thousands ha wide", Interval(3, 6), goldEntity="MEASUREMENT-AREA", goldNorm= "1500.0 ha")
    ensure(sentence= "Rice crops are 1 ha wide", Interval(3, 5), goldEntity="MEASUREMENT-AREA", goldNorm= "1.0 ha")
    ensure(sentence= "Rice crops are one ha wide", Interval(3, 5), goldEntity="MEASUREMENT-AREA", goldNorm= "1.0 ha")
    ensure(sentence= "Rice crops are ten ha wide", Interval(3, 5), goldEntity="MEASUREMENT-AREA", goldNorm= "10.0 ha")
    ensure(sentence= "Rice crops are twenty five ha wide", Interval(3, 5), goldEntity="MEASUREMENT-AREA", goldNorm= "25.0 ha")
    ensure(sentence= "Rice crops are twenty-five ha wide", Interval(3, 5), goldEntity="MEASUREMENT-AREA", goldNorm= "25.0 ha")
    ensure(sentence= "Rice crops are one hundred ha wide", Interval(3, 5), goldEntity="MEASUREMENT-AREA", goldNorm= "100.0 ha")
    ensure(sentence= "Rice crops are one thousand ha wide", Interval(3, 6), goldEntity="MEASUREMENT-AREA", goldNorm= "1000.0 ha")
    ensure(sentence= "Rice crops are one hundred thousand ha wide", Interval(3, 6), goldEntity="MEASUREMENT-AREA", goldNorm= "100000.0 ha")
  }

  // tests for recognizing fertilizer, seeds and yield measurement units
  it should "recognize literal measurement units" in {
    // these tests should pass 
    ensure(sentence= "Imports of rice in the decade 2008-2017 amounted on average to 1500000 tonnes", Interval(11, 13), goldEntity="MEASUREMENT-MASS", goldNorm="1500000.0 t")
    ensure(sentence= "They had yield potentials of 10 metric tons per hectare", Interval(5, 10), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="10.0 t/ha")
    ensure(sentence= "Such observations were replaced with a cap value of 700 kilograms per hectare", Interval(9, 13), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="700.0 kg/ha")
    ensure(sentence= "The production from the SRV was therefore 360000 tons of paddy", Interval(7, 9), goldEntity="MEASUREMENT-MASS", goldNorm="360000.0 t")
    ensure(sentence= "Total production was 6883 thousand tons", Interval(3, 6), goldEntity="MEASUREMENT-MASS", goldNorm="6883000.0 t")
    ensure(sentence= "During 2009-10, area under rice cultivation was 2883 thousand hectares", Interval(8, 11), goldEntity="MEASUREMENT-AREA", goldNorm="2883000.0 ha")
    ensure(sentence= "Senegal is forecast at 2.4 million MT", Interval(4, 7), goldEntity="MEASUREMENT-MASS", goldNorm="2400000.0 t")
    ensure(sentence= "To determine the effect of planting date on key agronomic traits in rice, an 8 yr data", Interval(15, 17), goldEntity="MEASUREMENT-DURATION", goldNorm="8.0 y")
    ensure(sentence= "Planting dates were tentatively spaced by 2 wk", Interval(6, 8), goldEntity="MEASUREMENT-DURATION", goldNorm="2.0 w")
    
    // I propose to ignore this test. If we handle the dot here, we will parse incorrectly all the numbers with decimals
    // ensure(sentence= "1.68 ton for one hectare as a result of that the rainfall", Interval(0, 5), goldEntity="MEASUREMENT", goldNorm="1.68 t/ha")
    // ensure(sentence= "Rice is planted in early May next 5% reduction is only 7 d after that (24 April)", Interval(12, 14), goldEntity="MEASUREMENT", goldNorm="7.0 d")
    // ensure(sentence= "Imports of rice in the decade 2008-2017 amounted on average to 1,500,000 tonnes", Interval(11, 13), goldEntity="MEASUREMENT", goldNorm="1500000.0 t")

    // I propose to ignore this test. If we handle the dot here, we will parse incorrectly all the numbers with decimals
    // ensure(sentence= "The production from the SRV was therefore 360.000 tons of paddy", Interval(7, 9), goldEntity="MEASUREMENT", goldNorm="360000.0 t")

    // measurements that contain number ranges should work
    ensure(sentence= "Weeding timing ranged from 2 to 17 days", Interval(3, 8), goldEntity="MEASUREMENT-DURATION", goldNorm="2.0 -- 17.0 d")
    
    // TODO: not sure what should be the output of such measurement '3 or 4 days'
    ensure(sentence= "and lasted 3 or 4 days in both wet seasons", Interval(4, 6), goldEntity="MEASUREMENT-DURATION", goldNorm="4.0 d")
    ensure(sentence= "ranged from 2.7 t ha-1 to 7.1 t ha-1", Interval(1, 9), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="2.7 -- 7.1 t/ha")
    ensure(sentence= "yields were between 8.8 t ha-1 and 9.2 t ha-1", Interval(2, 10), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="8.8 -- 9.2 t/ha")
  }

  it should "recognize shared units" in {
    ensure(sentence = "Target yields on average were set to 6.4, 7.9, and 7.1 t/ha in 2011WS , 2012DS , and 2013DS , respectively.", Interval(7,8), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="6.4 t/ha")
    ensure(sentence = "Target yields on average were set to 6.4, 7.9, and 7.1 t/ha in 2011WS , 2012DS , and 2013DS , respectively.", Interval(9,10), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="7.9 t/ha")
    ensure(sentence = "Target yields on average were set to 6.4, 7.9, and 7.1 t/ha in 2011WS , 2012DS , and 2013DS , respectively.", Interval(12,13), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="7.1 t/ha")
    ensure(sentence = "was estimated at 9 and 10 t / ha", Interval(3, 4), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="9.0 t/ha")
    ensure(sentence = "was estimated at 9 and 10 t / ha", Interval(5, 9), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="10.0 t/ha")
    ensure(sentence = "+ 100 kg ha-1 urea at 20 das + 50 kg ha-1 urea at 50 das", Interval(0, 3), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="100.0 kg/ha")
    ensure(sentence = "yield will increase from 3600 in 2000-2009 to 4500 kg ha-1 in 2090-2099", Interval(4, 5), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="3600.0 kg/ha")
  }

  it should "not recognize preposition `in` as `inch`" in {
    ensure(sentence = "released as Sahel 108 in Senegal in 1994", Interval(3,5), goldEntity="O", goldNorm="")
    ensure(sentence = "92% grew Sahel 108 in 2012DS", Interval(3,5), goldEntity="O", goldNorm="")
  }

  // TODO: this requires non trivial changes to the tokenizer
  /*
  // tests for recognizing units which are sticked to values
  it should "recognize measurement units which are sticked to values" in {
    ensure(sentence= "Single cropping rice area is 4561.9km2", Interval(5, 7), goldEntity="MEASUREMENT", goldNorm="4561.9 km2")
    ensure(sentence= "Application dosage is 200kg/ha for compound fertilizer and 180kg/ha for urea", Interval(3, 6), goldEntity="MEASUREMENT", goldNorm="200.0 kg/ha")
    ensure(sentence= "The maximum seed yield was (3.43ton ha-1) gained", Interval(6, 12), goldEntity="MEASUREMENT", goldNorm="3.43 t/ha")
  }
  */

  // tests for recognizing units which change their meaning after normalization
  it should "recognize measurement units which should not be normalized" in {
    // TODO: Mihai ==> How do we handle cases like (Mg/ha or Mg/m3) which shouldn't be normalized as this is one of the preferred unit for yield or application rate
    // ensure(sentence= "Genetically improved rice varieties have grain yield potential of 10 Mg ha-1", Interval(9, 12), goldEntity="MEASUREMENT", goldNorm="10.0 Mg/ha")
  }

  // tests for recognizing complex measurement units
  it should "recognize complex measurement units" in {
    ensure(sentence= "Recommended seed usage is 130 kg/ha", Interval(4, 8), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="130.0 kg/ha")
    ensure(sentence= "1.25 to 1.65 mt/ha higher on average", Interval(0, 6), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="1.25 -- 1.65 t/ha")
    // TODO: not handling ranging in a single token like this, yet
    //ensure(sentence= "With average yields of 6-7 mt/ha", Interval(4, 10), goldEntity="MEASUREMENT", goldNorm="6-7 t/ha")
    ensure(sentence= "Average yield reached 7.2 t ha-1 in 1999", Interval(3, 6), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="7.2 t/ha")
    ensure(sentence= "The Nakhlet farmers’ organization bought 7 tonnes of urea", Interval(6, 8), goldEntity="MEASUREMENT-MASS", goldNorm="7.0 t")
    // ensure(sentence= "Fertilizers were given to farmers proportionally to their cultivated area at the rate of 250 kg urea ha-1", Interval(14, 18), goldEntity="MEASUREMENT", goldNorm="250.0 kg/ha")
    
    // TODO: not handling ranging in a single token like this, yet
    // ensure(sentence= "Rainfed rice yields average 1-2 MT/hectare", Interval(4, 10), goldEntity="MEASUREMENT", goldNorm="1.0 -- 2.0 t/ha")
    
    // TODO: wondering if we can handle such measures:mxm
    // ensure(sentence= "having a gross plot size of 3.0 m × 6.0 m", Interval(6, 11), goldEntity="MEASUREMENT", goldNorm="18.0 m2")
   
    ensure(sentence= "500 mL acre-1 was applied on moist soil after 30-35 days of planting each crop", Interval(0, 3), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="500.0 ml/acre")
    ensure(sentence= "The total area represented in each image was 3.24 cm2", Interval(8, 10), goldEntity="MEASUREMENT-AREA", goldNorm="3.24 cm2")
    ensure(sentence= "Average yields, at 1 to 2 tonnes/ha, are much lower than in the SRV", Interval(4, 10), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="1.0 -- 2.0 t/ha")
    ensure(sentence= "Irrigated rice yields are consistently high, averaging 5 to 6 MT/hectare", Interval(8, 14), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="5.0 -- 6.0 t/ha")
    ensure(sentence= "Pandan Wangi has a grain yield of 4.94 tons/ha", Interval(7, 11), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="4.94 t/ha")
    
    // TODO: Constructions like kg N, P ha-1 aren't really units, so we need a fix for this
    // ensure(sentence= "close to the recommendations of 120 kg N ha-1", Interval(5, 11), goldEntity="MEASUREMENT", goldNorm="120.0 kg/ha")
    // ensure(sentence= "19 kg P ha-1 were applied in two top-dressed applications", Interval(0, 6), goldEntity="MEASUREMENT", goldNorm="19.0 kg/ha")
    // ensure(sentence= "cultivated area at the rate of 250 kg urea ha-1", Interval(7, 13), goldEntity="MEASUREMENT", goldNorm="250.0 kg/ha")
    // ensure(sentence= "Potassium (150 kg K2O ha-1) was split equally at basal fertilization", Interval(2, 8), goldEntity="MEASUREMENT", goldNorm="150.0 kg/ha")
    ensure(sentence= "East with land area of 147,141 Km2", Interval(5, 7), goldEntity="MEASUREMENT-AREA", goldNorm="147141.0 km2")
    
    // TODO: Temperatures measures need be to addressed
    // ensure(sentence= "Rice should not be planted when the average air and soil temperature is below 15 ˚C", Interval(14, 17), goldEntity="MEASUREMENT", goldNorm="15 ˚C")
    // ensure(sentence= "daily maximum temperature of 40-45 C in May", Interval(4, 8), goldEntity="MEASUREMENT", goldNorm="40.0 -- 45.0 °C")

    // TODO: not handling values hyphen separated with theit units
    // ensure(sentence= "Grain yield was determined from a 5-m2 area in each plot", Interval(6, 9), goldEntity="MEASUREMENT", goldNorm="5.0 m2")
    // ensure(sentence= "Punjab has 3.5 million ha under wheat cultivation with productivity of 5.2-ton ha-1 respectively.", Interval(11, 13), goldEntity="MEASUREMENT", goldNorm="5.2 t/ha")
  }

  // tests for mass and concentation units (Soil bulk density, volume basis etc)
  it should "recognize mass and concentration measurement units" in {
    ensure(sentence= "N content ranged from 0.37 to 0.71 g kg-1 soil", Interval(3, 9), goldEntity="MEASUREMENT-CONCENTRATION", goldNorm="0.37 -- 0.71 g/kg")
    ensure(sentence= "C content ranged from 4.4 to 7.9 mg g-1 soil, ", Interval(3, 9), goldEntity="MEASUREMENT-CONCENTRATION", goldNorm="4.4 -- 7.9 mg/g")
    
    // TODO: Need a fix for concentration units with dot ex: g.kg-1
    // ensure(sentence= "P-Olsen ranged from 4.3 to 17 g.kg-1 soil", Interval(2, 7), goldEntity="MEASUREMENT", goldNorm="4.3 -- 17.0 g/kg")
    // I propose to ignore this test. If we handle the dot here, we will parse incorrectly all the numbers with decimals
    // ensure(sentence= "with concentrations reaching 3.99 mg kg-1", Interval(3, 8), goldEntity="MEASUREMENT", goldNorm="3.99 mg/kg")
    // ensure(sentence= "with a corresponding increase in unit yield of 337.5 kg·ha-1 and only 249 kg·ha-1", Interval(8, 14), goldEntity="MEASUREMENT", goldNorm="337.5 kg/ha")

    ensure(sentence= "the irrigation water supply was above 700 mm", Interval(6, 8), goldEntity="MEASUREMENT-LENGTH", goldNorm="700.0 mm")
    
    // TODO: Fix for measurements units with Greek letters
    // ensure(sentence= "sugar 6976 µg/g", Interval(1, 5), goldEntity="MEASUREMENT", goldNorm="6976.0 µg/g")
    // ensure(sentence= "1.1 mg/g uronic acid", Interval(0, 4), goldEntity="MEASUREMENT", goldNorm="1.1 mg/g")
    // ensure(sentence= "731.5 µg/g protein", Interval(0, 4), goldEntity="MEASUREMENT", goldNorm="731.5 µg/g")
    // ensure(sentence= "Saturated water content 4.54 m3 m-3", Interval(3, 7), goldEntity="MEASUREMENT", goldNorm="4.54 m3/m3")
    // ensure(sentence= "Soil organic carbon (SOC) under fallow varied from 7.1 g kg-1", Interval(8, 13), goldEntity="MEASUREMENT", goldNorm="7.1 g/kg")
  }

  it should "recognize percentages" in {
    ensure("20% of the area is planted", Interval(0, 2), goldEntity = "PERCENTAGE", goldNorm = "20.0 %")
    ensure("20 pct of the area is planted", Interval(0, 2), goldEntity = "PERCENTAGE", goldNorm = "20.0 %")
  }

  it should "work correctly with en dashes" in {
    val endash = "\u2013"

    ensure(sentence= "Imports of rice in the decade 2008" + endash + "2017 amounted on average to 1500000 tonnes", Interval(13, 15), goldEntity="MEASUREMENT-MASS", goldNorm="1500000.0 t")
    ensure(sentence= "Imports of rice in the decade 2008" + endash + "2017 amounted on average to 1,500,000 tonnes", Interval(13, 15), goldEntity="MEASUREMENT-MASS", goldNorm="1500000.0 t")
    ensure(sentence= "Average yield reached 7.2 t ha" + endash + "1 in 1999", Interval(3, 6), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="7.2 t/ha")
    ensure(sentence= "Fertilizers were given to farmers proportionally to their cultivated area at the rate of 250 kg urea ha" + endash + "1", Interval(14, 18), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="250.0 kg/ha")
    //lowercase N ensure(sentence= "close to the recommendations of 120 kg N ha" + endash + "1", Interval(5, 9), goldEntity="MEASUREMENT", goldNorm="120.0 kg/ha")
    //lowercase P ensure(sentence= "19 kg P ha" + endash + "1 were applied in two top-dressed applications", Interval(0, 4), goldEntity="MEASUREMENT", goldNorm="19.0 kg/ha")
    ensure(sentence= "cultivated area at the rate of 250 kg urea ha" + endash + "1", Interval(6, 10), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="250.0 kg/ha")
    ensure(sentence= "farmers applied a combination of propanil 4 liters ha" + endash + "1", Interval(6, 9), goldEntity="MEASUREMENT-AREAL-DENSITY", goldNorm="4.0 l/ha")
    //interval ensure(sentence= "daily maximum temperature of 40" + endash + "45 C in May", Interval(4, 8), goldEntity="MEASUREMENT", goldNorm="40 -- 45 °C")
    ensure(sentence= "CEC varied from 18 to 29 cmol kg" + endash + "1", Interval(2, 5), goldEntity="MEASUREMENT-CONCENTRATION", goldNorm="18.0 -- 29.0 cmol/kg") // TODO: check on interval, get two BIO sets
    ensure(sentence= "C content ranged from 4.4 to 7.9 mg g" + endash + "1 soil, ", Interval(3, 9), goldEntity="MEASUREMENT-CONCENTRATION", goldNorm="4.4 -- 7.9 mg/g")
    ensure(sentence= "the rainfall during October and November was about (144 ml) during 2015" + endash + "2016 season may cause better moisture availability", Interval(9, 11), goldEntity="MEASUREMENT-VOLUME", goldNorm="144.0 ml")
  }

  it should "it should not crash on weird texts" in {
    ensure(sentence= "Sown cultivar and areaJaya: 15.0 haJaya: 27.5 haJaya: 26.6 haSahel 202: 27.5 haSahel 202: 22.5 haSahel 108: 12.5 haSahel 108: 0.9 haJaya: 5.0 haWeedingtype and rate2 l 2-4D ha–1+ 4 l Propanil ha–12 l 2-4D ha–1+ 4 l Propanil ha–1manual2 l 2-4D ha–1+ 4 l Propanil ha–12 l 2-4D ha–1+ 4 l Propanil",
        Interval(0, 1), goldEntity="", goldNorm = "")
  }

  //
  // End unit tests for date recognition.
  //

  it should "not hang" in {
    val text = "others 1,016 960 250 80 150 1,300 50 1,200 50 700 2,300 3,800 225 800 2 150 200 3,691 7,160 3 130 1,480 1,136 2,515 300 130 875 1,050 30 365400 3,775 Total 2487 3,450 8,575 825 19 112 Source : LM 12 / Saed The SSF 2020/2021 campaign is timidly being set up on the entire left bank of the Senegal River with the establishment of nurseries ."
    val timer = new Timer("Keith")

    timer.time {
//      TimeLimits.failAfter(Span(25, Seconds)) {
        numericParse(text)
//      }
    }
    println(s"Keith says: ${timer.elapsedToString()}")
  }

  //
  // Helper methods below this point
  //

  /** Makes sure that the given span has the right entity labels and norms */
  def ensure(sentence: String,
             span: Interval,
             goldEntity: String,
             goldNorm: String): Unit = {
    val (words, entities, norms) = numericParse(sentence)

    println("Verifying the following text:")
    println("Words:    " + words.mkString(", "))
    println("Entities: " + entities.mkString(", "))
    println("Norms:    " + norms.mkString(", "))

    if (goldEntity.nonEmpty) {
      var first = true
      for (i <- span.indices) {
        if (goldEntity == "O") {
          norms(i) should be(goldNorm)
        } else {
          val prefix = if (first) "B-" else "I-"
          val label = prefix + goldEntity

          entities(i) should be(label)
          norms(i) should be(goldNorm)

          first = false
        }

      }
    }
  }

  /** Runs the actual numeric entity recognizer */
  def numericParse(sentence: String): (Seq[String], Seq[String], Seq[String]) = {
    val doc = proc.annotate(sentence)
    val mentions = ner.extractFrom(doc)
    NumericUtils.mkLabelsAndNorms(doc, mentions)

    // assume 1 sentence per doc
    val sent = doc.sentences.head
    (sent.words, sent.entities.get, sent.norms.get)
  }
}
