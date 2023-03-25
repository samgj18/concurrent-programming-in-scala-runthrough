// In the following code snippet, we show you how to instantiate a ForkJoinPool
// implementation and submit a task that can be asynchronously executed:

import java.util.concurrent.ForkJoinPool // Scala alias is deprecated since 2.12, see: https://www.scala-lang.org/api/2.12.7/scala/concurrent/forkjoin/package$$ForkJoinPool$.html
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.annotation.tailrec

object CreateExecutor extends App {
  // One Executor implementation, introduced in JDK7
  val executor = new ForkJoinPool()

  executor.execute(new Runnable {
    def run() = {
      Instantiator.log("Asynchronous task")
    }
  })

  Thread.sleep(5000)

}

object CreateExecutorWithAwait extends App {
  val executor = new ForkJoinPool()

  executor.execute(new Runnable {
    def run() = {
      Instantiator.log(
        "This task will be awaited for completion due to the blocking call of awaitTermination"
      )
    }
  })

  executor.shutdown()
  executor.awaitTermination(60, TimeUnit.SECONDS)
}

object ExecutionContextGlobal extends App {
  val exctx =
    ExecutionContext.global // Internally implements a ForkJoinPool Executor

  exctx.execute(new Runnable {
    def run() = {
      Instantiator.log(
        "This task will be executed by the global ExecutionContext"
      )
    }
  })

  Thread.sleep(5000)
}

// We can also create an ExecutionContext from an Executor using `fromExecutor` and `fromExecutorService` methods:
object ExecutionContextCreate extends App {
  val pool =
    new ForkJoinPool(
      2
    ) // Parallelism level (# of worker threads in the thread pool)
  val exctx = ExecutionContext.fromExecutor(pool)

  exctx.execute(new Runnable {
    def run() = {
      Instantiator.log(
        "This task will be executed by the ExecutionContext created from a ForkJoinPool"
      )
    }
  })

  Thread.sleep(5000)
}

object ECUtil {
  def execute(body: => Unit) = ExecutionContext.global.execute(new Runnable {
    def run() = body
  })
}

object ExecutionContextSleep extends App {
  for (i <- 0 until 32) ECUtil.execute {
    Thread.sleep(2000)
    Instantiator.log(s"Task $i completed.")
  }
  Thread.sleep(10000)
}

// Recall the getUniqueId method from the previous section, which we implemented using synchronized blocks, let's rewrite it using Atomic primitives:

import java.util.concurrent.atomic._

object AtomicUid extends App {
  private val uid = new AtomicLong(0L)

  /** The incrementAndGet method is a complex linearizable operation, which is
    * to say atomic. Under the hood incrementAndGet does four things:
    *
    *   - Read the current value of the AtomicLong
    *   - Computes the new value by adding 1 to the current value
    *   - Writes the new value back to the AtomicLong
    *   - Returns the new value
    *
    * Hence every invocation of `getUniqueId` will return a unique value.
    */
  def getUniqueId: Long = uid.incrementAndGet()

  ECUtil.execute(Instantiator.log(s"Got unique ID: ${getUniqueId}"))
  Instantiator.log(s"Got another unique ID: ${getUniqueId}")

  var uidCount = 0L

// Recall previous implementation:
  def getUniqueIdSynchronized() = this.synchronized {
    val freshUid = uidCount + 1
    uidCount = freshUid
    freshUid
  }

  ECUtil.execute(Instantiator.log(s"Got unique ID: ${getUniqueIdSynchronized}"))
  Instantiator.log(s"Got another unique ID: ${getUniqueIdSynchronized}")

  // CAS (compare-and-swap), compare and swap is the fundamental building block of
  // all atomic operations. `incrementAndGet`, `addAndGet`, `getAndSet` ... are
  // all implemented using CAS operations. Conceptually, a CAS operation is the
  // equivalent of the following code:
  // def compareAndSet(oldValue: Long, newValue: Long): Boolean = {
  //     this.synchronized {
  //        if (this.get == oldValue) {
  //           this.set(newValue)
  //           true
  //           } else {
  //             false
  //          }
  //      }
  // }

  // Let's reimplement the getUniqueId method using a CAS operation
  @tailrec
  def getUniqueIdCAS: Long = {
    val oldUid = uid.get()
    val newUid = oldUid + 1
    if (uid.compareAndSet(oldUid, newUid)) newUid
    else getUniqueIdCAS
  }
}

// Lock Free Programming

// However, not all operations composed from atomic primitives are lock-free.
// Using atomic variables is a necessary precondition for lock-freedom, but it is not sufficient.
object AtomicLock extends App {
  private val lock = new AtomicBoolean(false)

  def mySynchronized(body: => Unit): Unit = {
    while (
      !lock.compareAndSet(false, true)
    ) {} // busy waiting as it constantly checks the value of the lock
    try body
    finally lock.set(false)
  }
  var count = 0
  for (i <- 0 until 10) ECUtil.execute {
    mySynchronized {
      count += 1
    }
  }
  Thread.sleep(1000)
  Instantiator.log(s"Count is $count")
}

// FileSystem API

object FileSystem {
  // 1. Ensure that only one thread can delete the file at the same time

  sealed trait State
  object Idle extends State
  object Creating extends State

  /** @param n
    *   how many concurrent copies are in progress
    */
  final case class Copying(val n: Int) extends State
  object Deleting extends State

  /** Model of a single file or directory entry in a file system.
    *
    * @param isDir
    *   true if the entry is a directory, false if it is a file
    */
  class Entry(val isDir: Boolean) {
    val state = new AtomicReference[State](Idle)
  }

  @tailrec private def prepareForDelete(entry: Entry): Boolean = {
    val s0 = entry.state.get()

    s0 match {
      case Idle =>
        // An important note about CAS instructions on atomic references is that uses reference equality and never calls equal method (even if it is overridden).
        // Note:
        // Referential equality is whether two references point to the same object in a heap -> Review playground.worksheet.sc
        if (entry.state.compareAndSet(s0, Deleting)) true
        else prepareForDelete(entry)
      case Creating =>
        Instantiator.log("File currently created, cannot delete"); false
      case Copying(n) =>
        Instantiator.log("File currently copied, cannot delete"); false
      case Deleting => false
    }
  }

}

object LazyValsCreate extends App {
  lazy val obj = new AnyRef
  lazy val non = s"made by ${Thread.currentThread.getName}"

  ECUtil.execute {
    Instantiator.log(s"EC sees obj = $obj")
    Instantiator.log(s"EC sees non = $non")
  }
  Instantiator.log(s"Main sees obj = $obj")
  Instantiator.log(s"Main sees non = $non")
  Thread.sleep(500)
}

// Running this program reveals that the Lazy initializer runs when the
// object is first referenced in the third line and not when it is declared.
object LazyValsObject extends App {
  object Lazy { Instantiator.log("Running Lazy constructor.") }
  Instantiator.log("Main thread is about to reference Lazy.")
  Lazy
  Instantiator.log("Main thread completed.")
}

// Singleton objects are implemented with the so-called double-checked locking idiom under the hood
// This concurrent programming pattern ensures that a lazy value is initialized by at most one thread when it is first accessed.
object LazyValsUnderTheHood extends App {
  // The @volatile annotation ensures that this field is visible to all threads.
  @volatile private var _bitmap = false
  private var _obj: AnyRef = _
  def obj = if (_bitmap) _obj
  else
    this.synchronized {
      // Here is where the double-checked locking idiom happens, 
      if (!_bitmap) {
        _obj = new AnyRef
        _bitmap = true
      }
      _obj
    }
  Instantiator.log(s"$obj")
  Instantiator.log(s"$obj")
}
