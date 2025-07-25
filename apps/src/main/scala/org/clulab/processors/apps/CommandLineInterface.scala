package org.clulab.processors.apps

import org.clulab.processors.Document
import org.clulab.processors.clu.BalaurProcessor
import org.clulab.serialization.CoNLLUSerializer
import org.clulab.utils.{FileUtils, StringUtils, WrappedArraySeq}

import java.io.PrintWriter
import scala.util.Using

object CommandLineInterface extends App {
  // specifies the input file
  val INPUT = "input"
  // specifies the output file; if not provided uses stdout
  val OUTPUT = "output"

  // input formats, other than raw text:
  val SENTS = "sentences"
  val TOKENS = "tokens"

  val props = StringUtils.argsToProperties(args)
  println(props)

  if (!props.containsKey(INPUT)) {
    // run in interactive mode because no input file was provided
    new ProcessorsShell().shell()
  } else {
    // process a file in batch mode
    val proc = new BalaurProcessor()

    val doc: Document =
      if (props.containsKey(SENTS)) {
        // one sentence per line; sentences are NOT tokenized
        val sents = FileUtils.getLinesFromFile(props.getProperty(INPUT))
        proc.annotateFromSentences(sents)
      } else if(props.containsKey(TOKENS)) {
        // one sentence per line; sentences are tokenized
        val sents = FileUtils.getLinesFromFile(props.getProperty(INPUT))
        val tokenizedSents = sents.map { sent =>
          val tokens = sent.split("\\s+")

          WrappedArraySeq(tokens).toImmutableSeq
        }
        proc.annotateFromTokens(tokenizedSents)
      } else {
        // assume raw text
        val rawText = FileUtils.getTextFromFile(props.getProperty(INPUT))
        proc.annotate(rawText)
      }

    val pw: PrintWriter =
      if(props.containsKey(OUTPUT)) {
        FileUtils.printWriterFromFile(props.getProperty(OUTPUT))
      } else {
        FileUtils.printWriterFromSystemOut()
      }

    Using.resource(pw) { pw  =>
      CoNLLUSerializer.saveCoNLLUExtended(pw, doc)
    }
  }
}
