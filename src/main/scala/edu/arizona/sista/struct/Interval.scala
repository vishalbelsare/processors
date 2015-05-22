package edu.arizona.sista.struct

/**
 *  An interval of integers.
 *
 *  @constructor create a new interval
 *  @param start the first element of the interval
 *  @param end the last element of the interval (exclusive)
 */
class Interval(val start: Int, val end: Int) extends IndexedSeq[Int] with Ordered[Interval] {
  require(start < end || (start == 0 && end == 0), "invalid range")

  override def toString: String = s"Interval($start, $end)"

  def length: Int = end - start

  def contains(i: Int): Boolean = i >= start && i < end

  def apply(index: Int): Int = {
    require(index >= 0 && index < length, "invalid index")
    start + index
  }

  def compare(that: Interval): Int =
    if (this.start > that.start) 1
    else if (this.start < that.start) -1
    else this.size - that.size

  /** returns true if the given interval is contained by this interval */
  def contains(that: Interval): Boolean =
    this.start <= that.start && this.end >= that.end

  /** returns true if there is any overlap between the members of the intervals */
  def overlaps(that: Interval): Boolean =
    if (this.start < that.start) {
      this.end > that.start
    } else if (this.start > that.start) {
      this.start < that.end
    } else true

  @deprecated("Please use Interval.overlaps instead", "processors 5.3")
  def intersects(that: Interval): Boolean = overlaps(that)

  // A precedes B
  //
  // A: #####
  // B:       #####
  def allenPrecedes(that: Interval): Boolean =
    this.end < that.start

  // A meets B
  //
  // A: #####
  // B:      #####
  def allenMeets(that: Interval): Boolean =
    this.end == that.start

  // A overlaps B
  //
  // A: #####
  // B:    #####
  def allenOverlaps(that: Interval): Boolean =
    this.start < that.start && this.end > that.start && this.end < that.end

  // A finishes B
  //
  // A:      #####
  // B: ##########
  def allenFinishes(that: Interval): Boolean =
    this.start > that.start && this.end == that.end

  // A contains B
  //
  // A: ##########
  // B:    #####
  def allenContains(that: Interval): Boolean =
    this.start < that.start && this.end > that.end

  // A starts B
  //
  // A: #####
  // B: ##########
  def allenStarts(that: Interval): Boolean =
    this.start == that.start && this.end < that.end

  // A equals B
  //
  // A: #####
  // B: #####
  def allenEquals(that: Interval): Boolean =
    this.start == that.start && this.end == that.end

  // A startedBy B
  //
  // A: ##########
  // B: #####
  def allenStartedBy(that: Interval): Boolean =
    that allenStarts this

  // A containedBy B
  //
  // A:    #####
  // B: ##########
  def allenContainedBy(that: Interval): Boolean =
    that allenContains this

  // A finishedBy B
  //
  // A: ##########
  // B:      #####
  def allenFinishedBy(that: Interval): Boolean =
    that allenFinishes this

  // A overlappedBy B
  //
  // A:    #####
  // B: #####
  def allenOverlappedBy(that: Interval): Boolean =
    that allenOverlaps this

  // A metBy B
  //
  // A:      #####
  // B: #####
  def allenMetBy(that: Interval): Boolean =
    that allenMeets this

  // A precededBy B
  //
  // A:        #####
  // B: #####
  def allenPrecededBy(that: Interval): Boolean =
    that allenPrecedes this
}

private[struct] object Empty extends Interval(0, 0) {
  override def toString: String = "Empty"
}

object Interval {
  /** the empty interval */
  val empty: Interval = Empty
  /** make an interval with a single element */
  def apply(i: Int): Interval = new Interval(i, i + 1)
  def apply(start: Int, end: Int): Interval = new Interval(start, end)
}
