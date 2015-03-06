package psp
package std

import api._

object PartialOrder {
  final class Impl[-A](val f: (A, A) => PCmp) extends AnyVal with PartialOrder[A] {
    def partialCompare(x: A, y: A) = f(x, y)
  }
  def apply[A](f: (A, A) => PCmp): PartialOrder[A] = new Impl[A](f)
}

object Order {
  import Cmp._

  def apply[A](f: (A, A) => Cmp): impl.OrderImpl[A]    = new impl.OrderImpl[A](f)
  def natural[A <: Comparable[A]](): impl.OrderImpl[A] = fromInt[A](_ compareTo _)
  def order[A: Order] : Order[A]                       = implicitly
  def fold(xs: Cmp*): Cmp                              = xs.m findOr (_ != EQ, EQ)
  def create[A](ord: Ordering[A]): Order[A]            = apply[A]((x, y) => longCmp(ord.compare(x, y)))

  def fromInt[A](f: (A, A) => Int): impl.OrderImpl[A]   = new impl.OrderImpl[A]((x, y) => longCmp(f(x, y)))
  def fromLong[A](f: (A, A) => Long): impl.OrderImpl[A] = new impl.OrderImpl[A]((x, y) => longCmp(f(x, y)))
}

object Builds {
  def apply[Elem, To](f: Each[Elem] => To): Builds[Elem, To]  = new Impl(f)
  def wrap[Elem, To](z: CanBuild[Elem, To]): Builds[Elem, To] = new Impl(xs => z() doto (b => xs foreach (b += _)) result)

  final class Impl[Elem, To](val f: Each[Elem] => To) extends AnyVal with Builds[Elem, To] {
    def build(xs: Each[Elem]): To      = f(xs)
    def apply(mf: Suspended[Elem]): To = build(Each(mf))
  }
}

object Empty {
  def apply[A](empty: A): Impl[A] = new Impl[A](empty)
  final class Impl[A](val empty: A) extends AnyVal with Empty[A]
}
