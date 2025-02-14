package org.clulab.odin.debugger.visualizer.extractor

import org.clulab.odin.debugger.visualization.HtmlVisualization
import org.clulab.odin.debugger.visualizer.html.HtmlVisualizing
import org.clulab.odin.impl.{CrossSentenceExtractor, Extractor, GraphExtractor, TokenExtractor}
import scalatags.Text
import scalatags.Text.all._

class HtmlExtractorVisualizer extends ExtractorVisualizer with HtmlVisualizing {

  def visualizeCrossSentenceExtractor(indent: Int, crossSentenceExtractor: CrossSentenceExtractor): Text.TypedTag[String] = {
    val textVisualizer = new TextExtractorVisualizer()
    val placeholder = raw("&nbsp;" * 2)
    val anchorExtraction = textVisualizer.extractTokenPattern(indent, crossSentenceExtractor.anchorPattern.pattern).map { case (name, value) =>
      (s"anchorPattern:pattern:$name", value)
    }
    val neighborExtraction = textVisualizer.extractTokenPattern(indent, crossSentenceExtractor.neighborPattern.pattern).map { case (name, value) =>
      (s"neighborPattern:pattern:$name", value)
    }
    val extractions = anchorExtraction ++ neighborExtraction
    val textVisualization = textVisualizer.visualizeCrossSentenceExtractor(indent, crossSentenceExtractor)
    val lines = textVisualization.lines
    val top = lines.takeWhile(_ != "anchorPattern:pattern:").toArray
    val topRow = toRow(top, 4)
    val botRows = extractions.flatMap { case (name, string) =>
      val headerRow = tr(
        td(placeholder),
        td(colspan := 3)(name)
      )
      val trailerRows = string.lines.map { line =>
        val number = line.takeWhile(_ != '.')
        val indent = line
          .drop(number.length + 2) // Skip . and first space.
          .takeWhile(_ == ' ')
        val rest = line.drop(number.length + 2 + indent.length)

        tr(
          td(placeholder),
          td(placeholder),
          td(`class` := right)(number),
          td(
            raw("&nbsp;" * indent.length),
            rest
          )
        )
      }

      Seq(headerRow) ++ trailerRows
    }

    table(`class` := bordered)(
      topRow,
      botRows
    )
  }

  def visualizeCrossSentenceExtractor(crossSentenceExtractor: CrossSentenceExtractor): Text.TypedTag[String] = {
    visualizeCrossSentenceExtractor(0, crossSentenceExtractor)
  }

  def visualizeGraphExtractor(indent: Int, graphExtractor: GraphExtractor): Text.TypedTag[String] = {
    val textVisualizer = new TextExtractorVisualizer()
    val placeholder = raw("&nbsp;" * 2)
    val extractions = textVisualizer.extractGraphPattern(indent, graphExtractor.pattern).map { case (name, value) =>
      (s"pattern:$name", value)
    }
    val textVisualization = textVisualizer.visualizeGraphExtractor(indent, graphExtractor)
    val lines = textVisualization.lines
    val top = lines.takeWhile(_ != "pattern:trigger:").toArray
    val topRow = toRow(top, 4)
    val botRows = extractions.flatMap { case (name, string) =>
      val headerRow = tr(
        td(placeholder),
        td(colspan := 3)(name)
      )
      val trailerRows = string.lines.map { line =>
        val number = line.takeWhile(_ != '.')
        val indent = line
          .drop(number.length + 2) // Skip . and first space.
          .takeWhile(_ == ' ')
        val rest = line.drop(number.length + 2 + indent.length)

        tr(
          td(placeholder),
          td(placeholder),
          td(`class` := right)(number),
          td(
            raw("&nbsp;" * indent.length),
            rest
          )
        )
      }

      Seq(headerRow) ++ trailerRows
    }

    table(`class` := bordered)(
      topRow,
      botRows
    )
  }

  def visualizeGraphExtractor(graphExtractor: GraphExtractor): Text.TypedTag[String] = {
    visualizeGraphExtractor(0, graphExtractor)
  }

  def visualizeTokenExtractor(tokenExtractor: TokenExtractor): Text.TypedTag[String] = {
    val textVisualizer = new TextExtractorVisualizer()
    val placeholder = raw("&nbsp;" * 2)
    val extractions = textVisualizer.extractTokenPattern(0, tokenExtractor.pattern).map { case (name, value) =>
      (s"pattern:$name", value)
    }

    val top = textVisualizer.visualizeTokenExtractor(0, tokenExtractor)
    val topRows = toRows(top.lines.toSeq, 4)
    val botRows = extractions.flatMap { case (name, string) =>
      val headerRow = tr(
        td(placeholder),
        td(colspan := 3)(name)
      )
      val trailerRows = string.lines.map { line =>
        val number = line.takeWhile(_ != '.')
        val indent = line
            .drop(number.length + 2) // Skip . and first space.
            .takeWhile(_ == ' ')
        val rest = line.drop(number.length + 2 + indent.length)

        tr(
          td(placeholder),
          td(placeholder),
          td(`class` := right)(number),
          td(
            raw("&nbsp;" * indent.length),
            rest
          )
        )
      }

      Seq(headerRow) ++ trailerRows
    }

    table(`class` := bordered)(
      topRows,
      botRows
    )
  }

  override def visualize(extractor: Extractor): HtmlVisualization = {

    val frag = extractor match {
      case tokenExtractor: TokenExtractor => visualizeTokenExtractor(tokenExtractor)
      case graphExtractor: GraphExtractor => visualizeGraphExtractor(graphExtractor)
      case crossSentenceExtractor: CrossSentenceExtractor => visualizeCrossSentenceExtractor(crossSentenceExtractor)
      case _ => ???
    }
    val visualization = new HtmlVisualization(frag)

    visualization
  }
}
