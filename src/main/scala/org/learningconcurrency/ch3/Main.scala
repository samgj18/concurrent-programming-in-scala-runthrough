// In the following code snippet, we show you how to instantiate a ForkJoinPool
// implementation and submit a task that can be asynchronously executed:

import java.util.concurrent.ForkJoinPool // Scala alias is deprecated since 2.12, see: https://www.scala-lang.org/api/2.12.7/scala/concurrent/forkjoin/package$$ForkJoinPool$.html
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.annotation.tailrec
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import scala.util.Try
import scala.collection.concurrent.TrieMap
import scala.util.Failure
import scala.util.Success
import java.util.concurrent.ConcurrentLinkedQueue

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
object ExecutionContextCreate extends App { val pool =
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

object LazyValsDeadlock extends App {
  object A { lazy val x: Int = B.y }
  object B { lazy val y: Int = A.x }
  ECUtil.execute { B.y }
  A.x
}

class FileSystem(dir: String) {
  import FileSystem._

  private val messages = new LinkedBlockingQueue[String]()

  val logger = new Thread {
    setDaemon(true)
    override def run(): Unit = while (true) {
      Instantiator.log(messages.take())
    }
  }

  logger.start()

  def logMessage(msg: String): Unit = messages.offer(msg)

  import java.io.File
  import org.apache.commons.io.FileUtils
  import scala.collection.concurrent.Map
  import scala.jdk.CollectionConverters._

  // val files: Map[String, FileSystem.Entry] = new ConcurrentHashMap().asScala

  // We can also use TrieMap to return a consistent state of the files
  val files: TrieMap[String, Entry] = new TrieMap[String, Entry]
  val rootDir = new File(dir)

  /** We place each f file along with a fresh Entry object into files by calling
    * put on the concurrent map. There is no need for a synchronized statement
    * around put, as the concurrent map takes care of synchronization. The put
    * operation is atomic, and it establishes a happens-before relationship with
    * subsequent get operations.
    *
    * What is the happens-before relationship?
    *
    * For a practical example of what it is visit:
    * https://www.youtube.com/watch?v=_LnKYiJOUho&ab_channel=DouglasSchmidt
    */
  for (f <- FileUtils.iterateFiles(rootDir, null, false).asScala) {
    files.put(f.getName, new FileSystem.Entry(false))
  }

  def allFiles: Iterable[String] = {
    for ((name, _) <- files) yield name
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

  def deleteFile(fileName: String): Unit = {
    files.get(fileName) match {
      case None => logMessage(s"Path '$fileName' does not exist")
      case Some(entry) if entry.isDir =>
        logMessage(s"Path '$fileName' is a directory")

      case Some(entry) =>
        ECUtil.execute {
          if (prepareForDelete(entry))
            if (FileUtils.deleteQuietly(new File(fileName)))
              files.remove(fileName)
        }
    }
  }

  @tailrec
  private def acquire(entry: Entry): Boolean = {
    val s0 = entry.state.get()

    s0 match {
      case Idle =>
        if (entry.state.compareAndSet(s0, Copying(1))) true else acquire(entry)
      case Copying(n) =>
        if (entry.state.compareAndSet(s0, Copying(n + 1))) true
        else acquire(entry)
      case _ =>
        logMessage("File inaccessible, cannot be copied at the moment")
        false
    }
  }

  @tailrec
  private def release(entry: Entry): Unit = {
    val s0 = entry.state.get()

    s0 match {
      case Creating => if (!entry.state.compareAndSet(s0, Idle)) release(entry)
      case Copying(n) => {
        val nState = if (n == 1) Idle else Copying(n - 1)
        if (!entry.state.compareAndSet(s0, nState)) release(entry)
      }
      case _ => logMessage("Ilegal state reached")
    }
  }

  def copyFile(src: String, dest: String): Unit = {
    files.get(src) match {
      case None => logMessage(s"File in '$src' does not exist")
      case Some(srcEntry) if !srcEntry.isDir =>
        ECUtil.execute {
          if (acquire(srcEntry)) Try {
            val destEntry = new Entry(false)
            destEntry.state.set(Creating)
            if (files.putIfAbsent(dest, destEntry) == None) Try {
              FileUtils.copyFile(new File(src), new File(dest))
            }
            release(destEntry)
            release(srcEntry)
          }
        }
      case _ => logMessage(s"File in '$src' is a directory")
    }
  }
}

object FileSystem {
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

}

object FileSystemExample extends App {
  val fileSystem = new FileSystem(".")
  fileSystem.deleteFile("test.txt")
  fileSystem.logMessage("Testing log!")
  fileSystem.allFiles.foreach(println)
}

object CollectionsIterators extends App {
  val queue = new LinkedBlockingQueue[String]
  for (i <- 1 to 5500) queue.offer(i.toString)
  ECUtil.execute {
    val it = queue.iterator
    while (it.hasNext) Instantiator.log(it.next())
  }
  for (i <- 1 to 5500) queue.poll()
  Thread.sleep(1000)
}

object CollectionsConcurrentMapBulk extends App {
  import scala.jdk.CollectionConverters._

  val names = new ConcurrentHashMap[String, Int]().asScala
  names("Johnny") = 0
  names("Jane") = 0
  names("Jack") = 0

  ECUtil.execute { for (n <- 0 until 10) names(s"John $n") = n }
  ECUtil.execute { for (n <- names) Instantiator.log(s"name: $n") }

  Thread.sleep(1000)
}

object CollectionsTrieMapBulk extends App {
  import scala.jdk.CollectionConverters._

  val names = new TrieMap[String, Int]

  names("Johnny") = 0
  names("Jane") = 0
  names("Jack") = 0

  ECUtil.execute { for (n <- 10 until 100) names(s"John $n") = n }
  ECUtil.execute {
    Instantiator.log("Snapshot Time")
    for (n <- names.map(_._1).toSeq.sorted) Instantiator.log(s"name: $n")
  }

  Thread.sleep(1000)
}

import scala.sys.process._

/** The scala.sys.process package contains a concise API for dealing with other
  * processes. We can run the child process synchronously—in which case, the
  * thread from the parent process that runs it waits until the child process
  * terminates—or asynchronously—in which case, the child process runs
  * concurrently with the calling thread from the parent process.
  */
object ProcessRun extends App {
  val command = "ls -a"
  val exitcode = command.!
  Instantiator.log(s"command exited with status $exitcode")

  def lineCount(filename: String): Int = {
    val output = s"wc $filename".!!
    output.trim.split(" ").head.toInt
  }

  Instantiator.log(lineCount("./build.sbt").toString)
}

object ProcessAsync extends App {
  val lsProcess = "ls -R /".run()
  Thread.sleep(1000)
  Instantiator.log("Timeout - killing ls!")
  lsProcess.destroy()
}

/** Implement a custom ExecutionContext class called PiggybackContext, which
  * executes Runnable objects on the same thread that calls execute. Ensure that
  * a Runnable object executing on the PiggybackContext can also call execute
  * and that exceptions are properly reported.
  */
object PiggybackContext extends App {
  object Context extends ExecutionContext {

    // Try executes the runnable.run in the same code
    override def execute(runnable: Runnable): Unit = Try(runnable.run()) match {
      case Failure(exception) => reportFailure(exception)
      case Success(_)         =>
    }

    override def reportFailure(cause: Throwable): Unit =
      Instantiator.log(cause.getMessage())

  }
}

/** Implement a TreiberStack class, which implements a concurrent stack
  * abstraction:
  *
  * class TreiberStack[T] { def push(x: T): Unit = ??? def pop(): T = ??? }
  */
class TreiberStack[T] {
  private val stack = new AtomicReference[List[T]](List.empty)

  @tailrec
  final def push(x: T): Unit = {
    val s0 = stack.get()
    val s1 = x :: s0

    if (!stack.compareAndSet(s0, s1)) push(x)
  }

  @tailrec
  final def pop(): T = {
    val s0 = stack.get()
    val s1 = s0.tail

    if (stack.compareAndSet(s0, s1)) s0.head
    else pop()
  }
}

/** class ConcurrentSortedList[T](implicit val ord: Ordering[T]) { def add(x:
  * T): Unit = ??? def iterator: Iterator[T] = ??? }
  *
  * We could also implement this in terms of Node
  */

class ConcurrentSortedList[T: Ordering] {
  private val sorted = new AtomicReference[List[T]](List.empty)

  @tailrec
  final def add(x: T): Unit = {
    val s0 = sorted.get()
    val s1 = (x :: s0).sorted

    if (!sorted.compareAndSet(s0, s1)) add(x)
  }

  // The Iterator object returned by the iterator method must correctly
  // traverse the elements of the list in the ascending order under the assumption
  // that there are no concurrent invocations of the add method
  final def iterator: Iterator[T] = sorted.get().iterator
}

class ConcurrentSortedListNode[T: Ordering] {
  case class Node(
      head: T,
      tail: AtomicReference[Option[Node]] =
        new AtomicReference[Option[Node]](None)
  )

  private val root = new AtomicReference[Option[Node]](None)

  @tailrec
  private def add(node: AtomicReference[Option[Node]], x: T): Unit = {
    val s0: Option[Node] = node.get()

    s0 match {
      case None => if (!node.compareAndSet(s0, Some(Node(x)))) add(node, x)
      case Some(Node(head, tail)) => {

        //  - negative if x < head -> Prepend
        //  - positive if x > head -> Append
        //  - zero otherwise (if x == head) -> Prepend
        if (implicitly[Ordering[T]].compare(x, head) <= 0) {
          val newNode = Node(x)
          newNode.tail.set(s0)
          if (!node.compareAndSet(s0, Some(newNode))) add(node, x)
        } else {
          // The head value is not lost
          add(tail, x)
        }
      }
    }
  }

  def add(x: T): Unit = {
    add(root, x)
  }

  def iterator: Iterator[T] = new Iterator[T] {
    private var nodeIter = root.get()

    override def hasNext: Boolean = nodeIter.isDefined == true

    override def next(): T = {
      root.get() match {
        case None => throw new NoSuchElementException("Basically impossible state right here")
        case Some(node) => {
          nodeIter = node.tail.get()
          node.head
        }
      }
    }
  }

}
