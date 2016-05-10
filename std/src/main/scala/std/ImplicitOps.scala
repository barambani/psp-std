package psp
package std

import java.{ lang => jl }
import api._, all._

final class AnyOps[A](val x: A) extends AnyVal {
  def any_s: String                         = s"$x"
  def id_## : Int                           = java.lang.System.identityHashCode(x)
  def id_==(y: Any): Boolean                = cast[AnyRef](x) eq cast[AnyRef](y)
  def matchIf[B : Empty](pf: A ?=> B): B    = matchOr(emptyValue[B])(pf)
  def matchOr[B](alt: => B)(pf: A ?=> B): B = if (pf isDefinedAt x) pf(x) else alt

  @inline def |>[B](f: A => B): B = f(x) // The famed forward pipe.
}
final class CharOps(val ch: Char) extends AnyVal {
  // def isAlphabetic = jl.Character isAlphabetic ch
  def isControl = jl.Character isISOControl ch
  // def isDigit      = jl.Character isDigit ch
  // def isLetter     = jl.Character isLetter ch
  // def isLower      = jl.Character isLowerCase ch
  // def isUpper      = jl.Character isUpperCase ch
  // def toLower      = jl.Character toLowerCase ch
  // def isSpace      = jl.Character isWhitespace ch
  def toUpper = jl.Character toUpperCase ch
  def to_s    = ch.toString

  def takeNext(len: Precise): CharRange = ch.toLong takeNext len map (_.toChar)
  def to(end: Char): CharRange          = LongInterval.to(ch.toLong, end) map (_.toChar)
  def until(end: Char): CharRange       = LongInterval.until(ch.toLong, end) map (_.toChar)
}
final class LongOps(val self: Long) extends AnyVal {
  def takeNext(len: Precise): LongRange = LongInterval.closed(self, len) map identity
  def to(end: Long): LongRange          = LongInterval.to(self, end) map identity
  def until(end: Long): LongRange       = LongInterval.until(self, end) map identity
}
final class DirectOps[A](val xs: Direct[A]) extends AnyVal {
  def head: A = apply(Index(0))
  def last: A = apply(lastIndex)
  def tail    = xs.drop(1)
  def init    = xs.dropRight(1)

  def reverse: Direct[A] = Each reversed xs
  def apply(i: Vdex): A  = xs elemAt i
  def indices: VdexRange = xs.size.indices
  def lastIndex: Index   = Index(xs.size.getLong - 1) // effectively maps both undefined and zero to no index.

  def containsIndex(vdex: Vdex): Boolean = indices containsLong vdex.indexValue

  @inline def foreachIndex(f: Vdex => Unit): Unit =
    ll.foreachLong(0, lastIndex.indexValue, i => f(Index(i)))
}

final class ForeachOps[A](val xs: Foreach[A]) {
  private[this] def to[CC[X]](implicit z: Builds[A, CC[A]]): CC[A] = z build each

  def trav: scTraversable[A]      = to[scTraversable] // flatMap, usually
  def seq: scSeq[A]               = to[scSeq] // varargs or unapplySeq, usually
  def toRefs: Each[Ref[A]]        = each map castRef
  def toRefArray(): Array[Ref[A]] = toRefs.force
  def each: Each[A]               = Each(xs foreach _)
  def view: View[A]               = each
}

final class PreciseOps(val size: Precise) {
  def indices: VdexRange = indexRange(0, size.getLong)
  def lastIndex: Index   = Index(size.getLong - 1) // effectively maps both undefined and zero to no index.

  def +(n: Precise): Precise             = size + n.getLong
  def -(n: Precise): Precise             = size - n.getLong
  def containsIndex(vdex: Vdex): Boolean = indices containsLong vdex.indexValue

  def min(rhs: Precise): Precise = all.min(size, rhs)
}

final class SizeOps(val lhs: Size) extends AnyVal {
  import Size._

  def getInt: Int = lhs match {
    case Finite(n) => n.toInt
    case s         => illegalArgumentException(s)
  }
  def isNonZero     = loBound =!= Zero
  def isZero        = lhs === Zero
  def atLeast: Size = Size.Range(lhs, Infinite)
  def atMost: Size  = Size.Range(Zero, lhs)

  def loBound: Atomic = lhs match {
    case Bounded(lo, _) => lo
    case x: Atomic      => x
  }

  /** For instance taking the union of two sets. The new size is
    *  at least the size of the larger operand, but at most the sum
    *  of the two sizes.
    */
  def union(rhs: Size): Size     = Size.Range(lhs max rhs, lhs + rhs)
  def intersect(rhs: Size): Size = Size.Range(Size.Zero, lhs min rhs)
  def diff(rhs: Size): Size      = Size.Range(lhs - rhs, lhs)

  def +(rhs: Size): Size = (lhs, rhs) match {
    case (Finite(l), Finite(r))                   => Finite(l + r)
    case (Infinite, Finite(_))                    => Infinite
    case (Finite(_), Infinite)                    => Infinite
    case (Infinite, Infinite)                     => Infinite
    case (Size.Range(l1, h1), Size.Range(l2, h2)) => Size.Range(l1 + l2, h1 + h2)
  }
  def -(rhs: Size): Size = (lhs, rhs) match {
    case (Finite(l), Finite(r))                   => Finite(l - r)
    case (Finite(_), Infinite)                    => Zero
    case (Infinite, Finite(_))                    => Infinite
    case (Infinite, Infinite)                     => Unknown
    case (Size.Range(l1, h1), Size.Range(l2, h2)) => Size.Range(l1 - h2, h1 - l2)
  }
  def min(rhs: Size): Size = Size.min(lhs, rhs)
  def max(rhs: Size): Size = Size.max(lhs, rhs)
}

final class FunOps[A, B](val f: Fun[A, B]) extends AnyVal { outer =>

  def applyOrElse(x: A, g: A => B): B       = cond(f isDefinedAt x, f(x), g(x))
  def zfold[C : Empty](x: A)(g: B => C): C  = cond(f isDefinedAt x, g(f(x)), emptyValue)
  def zapply(x: A)(implicit z: Empty[B]): B = zfold(x)(identity)
  def get(x: A): Option[B]                  = zfold(x)(some)
  def mapIn[C](g: C => A): Fun[C, B]        = AndThen(Opaque(g), f)
  def mapOut[C](g: B => C): Fun[A, C]       = AndThen(f, Opaque(g))

  def defaulted(g: A => B): Defaulted[A, B] = f match {
    case Defaulted(_, u) => Defaulted(g, u)
    case _               => Defaulted(g, f)
  }

  def filterIn(p: A => Boolean): FilterIn[A, B] = f match {
    case FilterIn(p0, u) => FilterIn(x => p0(x) && p(x), u)
    case _               => FilterIn(p, f)
  }

  def traced(in: A => Unit, out: B => Unit): Fun[A, B] =
    mapIn[A](x => doto(x)(in)) mapOut (x => doto(x)(out))
}