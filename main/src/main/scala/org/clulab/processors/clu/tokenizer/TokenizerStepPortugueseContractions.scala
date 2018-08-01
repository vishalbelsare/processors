package org.clulab.processors.clu.tokenizer

import scala.collection.mutable.ArrayBuffer

/**
  * Resolves Portugese contractions
  * Author: dane
  * Author: mihais
  * Date: 7/10/2018
  */
class TokenizerStepPortugueseContractions extends TokenizerStep {
  // is this word ALL CAPS, Sentence Caps, or lowercase?
  protected def cap(s: String): String = {
    val letters = s.filter(_.isLetter)
    letters match {
      case "" => "lower"
      case lower if !letters.head.isUpper => "lower"
      case upper if letters.length > 1 && letters.forall(c => !c.isLower) => "upper"
      case sentenceCaps if !letters.head.isLower => "sentence"
      case _ => "lower"
    }
  }

  //
  protected def matchCase(source: String, target: String): String = cap(source) match {
    case "lower" => target.toLowerCase
    case "sentence" => target.head.toUpper +: target.tail
    case "upper" => target.toUpperCase
  }

  override def process(inputs:Array[RawToken]): Array[RawToken] = {
    //
    // We must separate important linguistic constructs here
    // TODO: this is slow. This should be handled in the Antlr grammar
    //

    val tokens = new ArrayBuffer[RawToken]()

    for(input <- inputs) {
      //  TODO: change IFs to a MATCH (improve readability)
      if("""(?i)^do$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "o")
      }
      else if("""(?i)^da$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "a")
      }
      else if("""(?i)^dos$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "os")
      }
      else if("""(?i)^das$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "as")
      }
      else if("""(?i)^dum$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "um")
      }
      else if("""(?i)^duma$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "uma")
      }
      else if("""(?i)^duns$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "uns")
      }
      else if("""(?i)^dumas$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "umas")
      }
      else if("""(?i)^dele$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "ele")
      }
      else if("""(?i)^dela$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "ela")
      }
      else if("""(?i)^deles$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "eles")
      }
      else if("""(?i)^delas$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "delas")
      }
      else if("""(?i)^deste$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "este")
      }
      else if("""(?i)^desta$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "esta")
      }
      else if("""(?i)^destes$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "estes")
      }
      else if("""(?i)^destas$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "estas")
      }
      else if("""(?i)^desse$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "esse")
      }
      else if("""(?i)^dessa$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "essa")
      }
      else if("""(?i)^desses$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "esses")
      }
      else if("""(?i)^dessas$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "essas")
      }
      else if("""(?i)^daquele$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "aquele")
      }
      else if("""(?i)^daquela$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "aquela")
      }
      else if("""(?i)^daqueles$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "aqueles")
      }
      else if("""(?i)^daquelas$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "aquelas")
      }
      else if("""(?i)^no$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "o")
      }
      else if("""(?i)^na$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "a")
      }
      else if("""(?i)^nos$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "os")
      }
      else if("""(?i)^nas$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "as")
      }
      else if("""(?i)^num$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "um")
      }
      else if("""(?i)^numa$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "uma")
      }
      else if("""(?i)^nuns$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "uns")
      }
      else if("""(?i)^numas$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "umas")
      }
      else if("""(?i)^nele$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "ele")
      }
      else if("""(?i)^nela$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "ela")
      }
      else if("""(?i)^neles$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "eles")
      }
      else if("""(?i)^nelas$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "elas")
      }
      else if("""(?i)^neste$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "este")
      }
      else if("""(?i)^nesta$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "esta")
      }
      else if("""(?i)^nestes$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "estes")
      }
      else if("""(?i)^nestas$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "estas")
      }
      else if("""(?i)^nesse$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "esse")
      }
      else if("""(?i)^nessa$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "essa")
      }
      else if("""(?i)^nesses$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "esses")
      }
      else if("""(?i)^nessas$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "essas")
      }
      else if("""(?i)^naquele$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "aquele")
      }
      else if("""(?i)^naquela$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "aquela")
      }
      else if("""(?i)^naqueles$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "naqueles")
      }
      else if("""(?i)^naquelas$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "naquelas")
      }
      else if("""(?i)^ao$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "a"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "o")
      }
      else if("""(?i)^à$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "a"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition, "a")
      }
      else if("""(?i)^aos$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "a"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "os")
      }
      else if("""(?i)^às$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "a"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition, "as")
      }
      else if("""(?i)^àquele$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "a"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition, "aquele")
      }
      else if("""(?i)^àquela$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "a"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition, "aquela")
      }
      else if("""(?i)^àqueles$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "a"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition, "aqueles")
      }
      else if("""(?i)^àquelas$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "a"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition, "aquelas")
      }
      // last token should be the last character
      else if("""(?i)^pelo$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "por"))
        tokens += RawToken(input.raw.substring(3), input.beginPosition+3, "o")
      }
      // last token should be the last character
      else if("""(?i)^pela$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "por"))
        tokens += RawToken(input.raw.substring(3), input.beginPosition+3, "a")
      }
      // TODO: Sometimes 'pelos' means 'por' + 'eles'
      // the TODO above can't be infered from the raw word only
      else if("""(?i)^pelos$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "por"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition, "os")
      }
      // TODO: Sometimes 'pelas' means 'por' + 'elas'
      // the TODO above can't be infered from the raw word only
      else if("""(?i)^pelas$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "por"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition, "as")
      }
      // TODO:
      // doutros -> de outros
      // doutras -> de outras
      // doutra -> de outra
      // doutro -> de outro
      else if("""(?i)^doutr(os|as|o|a)$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, input.raw.substring(1))
      }
      // noutras -> em outras
      // noutros -> em outros
      // noutra -> em outra
      // noutro -> em outro
      else if("""(?i)^noutr(os|as|o|a)$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, input.raw.substring(1))
      }      
      // dalguns -> de alguns
      // dalgumas -> de algumas
      // dalguma -> de alguma
      // dalgum -> de algum
      // dalguém -> de alguém
      // dali -> de ali
      else if("""(?i)^dal(guns|gumas|guma|gum|guém|i)$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, input.raw.substring(1))
      }
      // nalguns - em - alguns
      // nalgumas - em algumas
      else if("""(?i)^nal(guns|gumas)$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "em"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, input.raw.substring(1))
      }
      // donde - de onde
      else if("""(?i)^donde$""".r.findFirstIn(input.raw).isDefined) {
        tokens += RawToken(input.raw.substring(0, 1), input.beginPosition, matchCase(input.raw, "de"))
        tokens += RawToken(input.raw.substring(1), input.beginPosition+1, "onde")
      }
      // any other token
      else {
        tokens += input
      }
    }

    tokens.toArray
  }
}

