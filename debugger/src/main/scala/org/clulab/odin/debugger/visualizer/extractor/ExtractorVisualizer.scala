package org.clulab.odin.debugger.visualizer.extractor

import org.clulab.odin.debugger.visualization.Visualization
import org.clulab.odin.impl.{Done, Extractor, Inst, MatchLookAhead, MatchLookBehind, MatchMention, MatchSentenceEnd, MatchSentenceStart, MatchToken, Pass, Reasoned, SaveEnd, SaveStart, Sourced, Split}

case class InstChild(name: String, inst: Inst, wide: Boolean)

abstract class ExtractorVisualizer() {
  def visualize(extractor: Extractor): Visualization

  def getChildren(inst: Inst): List[InstChild] = {

    def mkNextChild(inst: Inst, wide: Boolean): InstChild =
        InstChild("next", inst.getNext, wide)

    val children = inst match {
      case Done => List.empty
      case inst: Pass => List(mkNextChild(inst, true))
      case inst: Split => List(
        InstChild("lhs", inst.lhs, true),
        InstChild("rhs", inst.rhs, true)
      )
      case inst: SaveStart => List(mkNextChild(inst, true))
      case inst: SaveEnd => List(mkNextChild(inst, true))
      case inst: MatchToken => List(mkNextChild(inst, false))
      case inst: MatchMention => List(mkNextChild(inst, false))
      case inst: MatchSentenceStart => List(mkNextChild(inst, true))
      case inst: MatchSentenceEnd => List(mkNextChild(inst, true))
      case inst: MatchLookAhead => List(
        mkNextChild(inst, true),
        InstChild("start", inst.start, true)
      )
      case inst: MatchLookBehind => List(
        mkNextChild(inst, true),
        InstChild("start", inst.start, true)
      )
    }

    children
  }

  def extractInst(start: Inst): List[Inst] = {

    @annotation.tailrec
    def loop(todos: List[Inst], visiteds: Set[Inst], dones: List[Inst]): List[Inst] = {
      todos match {
        case Nil => dones
        case head :: tail =>
          if (visiteds(head)) loop(tail, visiteds, dones)
          else loop(getChildren(head).map(_.inst) ++ tail, visiteds + head, head :: dones)
      }
    }

    val unsortedInsts = loop(List(start), Set.empty, List.empty)
    val sortedInsts = unsortedInsts.sortBy(_.getPosId)

    assert(sortedInsts.head.getPosId == 0)
    assert(sortedInsts.head == Done)
    assert(start.getPosId == 1)
    sortedInsts.tail.headOption.foreach { tailHead =>
      assert(tailHead == start)
    }

    sortedInsts
  }

  def getSource(sourced: Sourced[_], isFirst: Boolean = false): String = {
    sourced.sourceOpt.map { source =>
      val prefix = if (isFirst) "" else ", "

      s"""${prefix}source = "$source""""
    }.getOrElse("")
  }

  def getReason(reasoned: Reasoned, isFirst: Boolean = false): String = {
    val reason = reasoned.reason
    val prefix = if (isFirst) "" else ", "

    s"""${prefix}source = "$reason""""
  }
}
