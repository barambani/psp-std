package psp
package std

import java.{ lang => jl }
import api._, all._, StdEq._

final class AnyOps[A](val x: A) extends AnyVal {
  def any_s: String                         = s"$x"
  def id_## : Int                           = java.lang.System.identityHashCode(x)
  def id_==(y: Any): Boolean                = cast[AnyRef](x) eq cast[AnyRef](y)
  def matchIf[B: Empty](pf: A ?=> B): B     = matchOr(emptyValue[B])(pf)
  def matchOr[B](alt: => B)(pf: A ?=> B): B = if (pf isDefinedAt x) pf(x) else alt

  @inline def |>[B](f: A => B): B = f(x)  // The famed forward pipe.

  // BooleanAlgebra[A]
  def && (rhs: A)(implicit z: BooleanAlgebra[A]): A = z.and(x, rhs)
  def || (rhs: A)(implicit z: BooleanAlgebra[A]): A = z.or(x, rhs)
  def unary_!(implicit z: BooleanAlgebra[A]): A     = z complement x

  // Eq[A]
  def ===(rhs: A)(implicit z: Eq[A]): Boolean = z.eqv(x, rhs)
  def =!=(rhs: A)(implicit z: Eq[A]): Boolean = !z.eqv(x, rhs)

  // Order[A]
  def < (rhs: A)(implicit z: Order[A]): Boolean = z.cmp(x, rhs) eq Cmp.LT
  def <=(rhs: A)(implicit z: Order[A]): Boolean = z.cmp(x, rhs) ne Cmp.GT
  def > (rhs: A)(implicit z: Order[A]): Boolean = z.cmp(x, rhs) eq Cmp.GT
  def >=(rhs: A)(implicit z: Order[A]): Boolean = z.cmp(x, rhs) ne Cmp.LT
}
final class CharOps(val ch: Char) extends AnyVal {
  // def isAlphabetic = jl.Character isAlphabetic ch
  def isControl    = jl.Character isISOControl ch
  // def isDigit      = jl.Character isDigit ch
  // def isLetter     = jl.Character isLetter ch
  // def isLower      = jl.Character isLowerCase ch
  // def isUpper      = jl.Character isUpperCase ch
  // def toLower      = jl.Character toLowerCase ch
  // def isSpace      = jl.Character isWhitespace ch
  def toUpper      = jl.Character toUpperCase ch
  def to_s         = ch.toString
}
final class IntOps(val self: Int) extends AnyVal {
  // def abs: Int                         = scala.math.abs(self)
  def downTo(end: Int): Direct[Int]    = Consecutive.downTo(self, end)
  def takeNext(len: Precise): IntRange = until(self + len.getInt)
  def to(end: Int): IntRange           = Consecutive.to(self, end)
  def until(end: Int): IntRange        = Consecutive.until(self, end)
}

final class LongOps(val self: Long) extends AnyVal {
  def to(end: Long): LongRange    = safeLongToInt(self) to safeLongToInt(end) map (_.toLong)
  def until(end: Long): LongRange = safeLongToInt(self) until safeLongToInt(end) map (_.toLong)
}

/** Extension methods for scala library classes.
 *  We'd like to get away from all such classes,
 *  but scala doesn't allow it.
 */
final class OptionOps[A](val x: Option[A]) extends AnyVal {
  def or(alt: => A): A              = x getOrElse alt
  def toVec: Vec[A]                 = this zfold (x => vec(x))
  def zfold[B: Empty](f: A => B): B = x.fold[B](emptyValue)(f)
  def zget(implicit z: Empty[A]): A = x getOrElse z.empty
  def | (alt: => A): A              = x getOrElse alt
}
final class TryOps[A](val x: Try[A]) extends AnyVal {
  def | (expr: => A): A = x.toOption | expr
  def fold[B](f: Throwable => B, g: A => B): B = x match {
    case Success(x) => g(x)
    case Failure(t) => f(t)
  }
}

/** Methods requiring us to have additional knowledge, by parameter or type class.
 *  We keep the interface simple and foolproof by establishing thet instance
 *  first and only offering the methods after that.
 *
 *  But.
 *
 *  This approach, so nice in principle, stretches scala's willingness to connect
 *  implicits past its abilities. It works on psp collections, but when we depend on
 *  an implicit to entire Viewville in the first place, then these methods become
 *  out of reach without an implicit call to .m to become a view.
 *
 *  The search continues.
 */
class HasEqOps[A](xs: View[A])(implicit z: Eq[A]) {
  def contains(x: A): Boolean = xs exists (_ === x)
  def distinct: View[A]       = xs.zfoldl[Vec[A]]((res, x) => if (new HasEqOps(res) contains x) res else res :+ x)
  def indexOf(x: A): Index    = xs indexWhere (_ === x)
  def toSet: ExSet[A]         = xs.toExSet

  // def indicesOf(x: A): View[Index]                = xs indicesWhere (_ === x)
  // def mapOnto[B](f: A => B): ExMap[A, B]          = toSet mapOnto f
  // def toBag: Bag[A]                               = xs groupBy identity map (_.size.getInt)
  // def without(x: A): View[A]                      = xs filterNot (_ === x)
  // def withoutEmpty(implicit z: Empty[A]): View[A] = this without z.empty
}
class HasHashOps[A](xs: View[A])(implicit z: Hash[A]) extends HasEqOps[A](xs)(z) {
  override def toSet: ExSet[A] = xs.toHashSet
}


final class DirectOps[A](val xs: Direct[A]) extends AnyVal {
  def head: A = apply(Index(0))
  def last: A = apply(lastIndex)
  def tail    = xs.drop(1)
  def init    = xs.dropRight(1)

  def reverse: Direct[A] = Direct reversed xs
  def apply(i: Vdex): A  = xs elemAt i
  def indices: VdexRange = indexRange(0, xs.size.getInt)
  def lastIndex: Index   = Index(xs.size.getLong - 1)  // effectively maps both undefined and zero to no index.

  def containsIndex(vdex: Vdex): Boolean = indices containsInt vdex.getInt

  @inline def foreachIndex(f: Vdex => Unit): Unit = (
    if (xs.size.isNonZero)
      ll.foreachConsecutive(0, lastIndex.getInt, i => f(Index(i)))
  )
}

final class ForeachOps[A](val xs: Foreach[A]) {
  private[this] def to[CC[X]](implicit z: Builds[A, CC[A]]): CC[A] = z build each

  def trav: scTraversable[A]      = to[scTraversable] // flatMap, usually
  def seq: scSeq[A]               = to[scSeq]         // varargs or unapplySeq, usually
  def toRefs: Each[Ref[A]]        = each map castRef
  def toRefArray(): Array[Ref[A]] = toRefs.force
  def each: Each[A]               = Each(xs foreach _)
  def view: View[A]               = each
}
final class DocOps(val lhs: Doc) extends AnyVal {
  def doc: Doc                             = lhs
  def render(implicit z: Renderer): String = z show lhs
  // def isEmpty: Boolean                     = lhs eq emptyValue[Doc]

  def ~(rhs: Doc): Doc   = Doc.Cat(lhs, rhs)
  // def <>(rhs: Doc): Doc  = if (lhs.isEmpty) rhs else if (rhs.isEmpty) lhs else lhs ~ rhs
  // def <+>(rhs: Doc): Doc = if (lhs.isEmpty) rhs else if (rhs.isEmpty) lhs else lhs ~ " " ~ rhs
}

final class PreciseOps(val size: Precise) {
  def toInt: Int         = size.getInt
  def indices: VdexRange = indexRange(0, size.getInt)
  def lastIndex: Index   = Index(size.getLong - 1)  // effectively maps both undefined and zero to no index.

  // def + (n: Precise): Precise         = size + n.get
  def - (n: Precise): Precise            = size - n.get
  def containsIndex(vdex: Vdex): Boolean = indices containsInt vdex.getInt

  def min(rhs: Precise): Precise = all.min(size, rhs)

  // if (size <= rhs) size else rhs
  // def max(rhs: Precise): Precise = if (size >= rhs) size else rhs

  // @inline def foreachIndex(f: Index => Unit): Unit  = if (size.get > 0L) ll.foreachConsecutive(0, lastIndex.getInt, i => f(Index(i)))
  // @inline def foreachIntIndex(f: Int => Unit): Unit = if (size.get > 0L) ll.foreachConsecutive(0, lastIndex.getInt, f)
}

// final class InputStreamOps(val in: InputStream) extends AnyVal {
//   def buffered: BufferedInputStream = in match {
//     case in: BufferedInputStream => in
//     case _                       => new BufferedInputStream(in)
//   }
//   def slurp(): Array[Byte]             = lowlevel.Streams slurp buffered
//   def slurp(len: Precise): Array[Byte] = lowlevel.Streams.slurp(buffered, len)
// }

final class SizeOps(val lhs: Size) extends AnyVal {
  import Size._, StdEq._

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

  def + (rhs: Size): Size = (lhs, rhs) match {
    case (Finite(l), Finite(r))                   => Finite(l + r)
    case (Infinite, Finite(_))                    => Infinite
    case (Finite(_), Infinite)                    => Infinite
    case (Infinite, Infinite)                     => Infinite
    case (Size.Range(l1, h1), Size.Range(l2, h2)) => Size.Range(l1 + l2, h1 + h2)
  }
  def - (rhs: Size): Size = (lhs, rhs) match {
    case (Finite(l), Finite(r))                   => Finite(l - r)
    case (Finite(_), Infinite)                    => Zero
    case (Infinite, Finite(_))                    => Infinite
    case (Infinite, Infinite)                     => Unknown
    case (Size.Range(l1, h1), Size.Range(l2, h2)) => Size.Range(l1 - h2, h1 - l2)
  }
  def min(rhs: Size): Size = Size.min(lhs, rhs)
  def max(rhs: Size): Size = Size.max(lhs, rhs)
}

final class FunOps[A, B](val f: Fun[A, B]) extends AnyVal {
  outer =>

  def applyOrElse(x: A, g: A => B): B       = cond(f isDefinedAt x, f(x), g(x))
  def zfold[C: Empty](x: A)(g: B => C): C   = cond(f isDefinedAt x, g(f(x)), emptyValue)
  def zapply(x: A)(implicit z: Empty[B]): B = zfold(x)(identity)
  def get(x: A): Option[B]                  = zfold(x)(some)
  def mapIn[C](g: C => A): Fun[C, B]        = AndThen(Fun(g), f)
  def mapOut[C](g: B => C): Fun[A, C]       = AndThen(f, Fun(g))

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
