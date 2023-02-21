package org.learningconcurrency.ch1

import org.learningconcurrency.ch1.ExerciseChapterOne.Pair
import scala.annotation.tailrec

object ExerciseChapterOne extends App {
  private def square(x: Int): Int = x * x

  private val s = square(5)
  println(s"Result: $s")

  /*  Exercise 1:
   * Implement the `compose` function
   */
  def compose[A, B, C](g: B => C, f: A => B): A => C = a => g(f(a))

  /* Exercise 2:
   * Implement the `fuse` function
   */
  def fuse[A, B](a: Option[A], b: Option[B]): Option[(A, B)] =
    for {
      aA <- a
      aB <- b
    } yield (aA, aB)

  /* Exercise 3:
   * Implement a `check` method, which takes a set of values of the type T and a function of the type T => Boolean.
   */
  def check[T](xs: Seq[T])(pred: T => Boolean): Boolean = xs.forall(pred)

  /* Exercise 4:
   * Modify the Pair class from this chapter so that it can be used in a pattern match.
   *
   * There are two ways to do this:
   * 1. Turning the class to a case class, which already implements hashCode and equals among other methods
   * 2. Implementing the unapply method in the companion object
   */

  class Pair[P, Q](val first: P, val second: Q)

  object Pair {
    def unapply[P, Q](p: Pair[P, Q]): Option[(P, Q)] = Some((p.first, p.second))
  }

  /* Exercise 5:
   * Implement a permutations function, which, given a string, returns a sequence of strings that are lexicographic permutations of the input string.
   */
  def permutations(x: String): Seq[String] = {

    def permute(x: String): Seq[String] = {
      if (x.length == 1) Seq(x)
      else {
        for {
          i <- x.indices
          p <- permute(x.take(i) + x.drop(i + 1))
        } yield x(i) + p
      }
    }

    assert(permute(x).length == factorial(x.length))

    permute(x)

  }

  @tailrec
  def factorial(value: Int, acc: Int = 1): Int = {
    if (value == 0) acc
    else factorial(value - 1, acc * value)
  }

}
