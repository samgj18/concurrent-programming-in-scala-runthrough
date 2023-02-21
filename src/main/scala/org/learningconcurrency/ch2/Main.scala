object ThreadsCreation extends App {

  /** Starting an independent thread of computation consists of two steps:
    *   - Create a new thread object to allocate the memory for the stack and
    *     thread state.
    *   - To start the computation, we need to call the start method on this
    *     object
    */
  class MyThread extends Thread {
    override def run(): Unit = {
      println("New thread running.")
    }
  }
  val t = new MyThread

  /** Calling the start method eventually results in executing the run method
    * from the new thread:
    *   - First, the OS is notified that t must start executing.
    *   - Then the OS decides to assign the new thread to some processor, (this
    *     is largely out of the programmer's control).
    *   - After the main thread starts the new thread t, it calls its join
    *     method. This method halts the execution of the main thread until t
    *     completes its execution.
    *   - We can say that the join operation puts the main thread into the
    *     waiting state until t terminates.
    *
    * The waiting thread yields control back to the processor, and the OS can
    * assign that processor to some other thread.
    */
  t.start()
  t.join()
  println("New thread joined.")
}

object ThreadsMain extends App {
  val t: Thread = Thread.currentThread
  val name = t.getName
  println(s"I am the thread $name")
}

object ThreadsCommunicate extends App {
  import Instantiator.{thread, log}
  var uidCount = 0L

  var result: String = null
  val t = thread { result = "\nTitle\n" + "=" * 5 }
  t.join()
  log(result)

  def getUniqueId() = {
    val freshUid = uidCount + 1
    uidCount = freshUid
    freshUid
  }

  def getUniqueIdSynchronized() = this.synchronized {
    val freshUid = uidCount + 1
    uidCount = freshUid
    freshUid
  }

  def printUniqueIds(n: Int): Unit = {
    val uids = for (i <- 0 until n) yield getUniqueIdSynchronized()
    log(s"Generated uids: $uids")
  }
  val t1 = thread { printUniqueIds(5) }
  printUniqueIds(5)
  t.join()
}

object Instantiator {

  def thread(body: => Unit): Thread = {
    val t = new Thread {
      override def run() = body
    }
    t.start()
    t
  }

  def log(msg: String): Unit = println(s"${Thread.currentThread.getName}: $msg")
}

/** The add method calls logTransfer from inside the synchronized statement, and
  * logTransfer first obtains the transfers monitor. Importantly, this happens
  * without releasing the account monitor. If the transfers monitor is currently
  * acquired by some other thread, the current thread goes into the blocked
  * state without releasing its monitors.
  */
object SynchronizedNesting extends App {

  import Instantiator.{thread, log}
  import scala.collection._

  private val transfers = mutable.ArrayBuffer[String]()
  def logTransfer(name: String, n: Int) = transfers.synchronized {
    transfers += s"transfer to account '$name' = $n"
  }

  class Account(val name: String, var money: Int)

  def add(account: Account, n: Int) = account.synchronized {
    account.money += n
    if (n > 10) logTransfer(account.name, n)
  }

  // In the following example, the main application creates two separate accounts
  // and three threads that execute transfers. Once all the threads complete their
  // transfers, the main thread outputs all the transfers that were logged:
  val jane = new Account("Jane", 100)
  val john = new Account("John", 200)

  val t1 = thread { add(jane, 5) }
  val t2 = thread { add(john, 50) }
  val t3 = thread { add(jane, 70) }

  t1.join(); t2.join(); t3.join()

  log(s"--- transfers ---\n$transfers")
}

object SynchronizedDeadlock extends App {
  import Instantiator.{thread, log}

  import SynchronizedNesting.{Account => DeadlockAccount}
  def send(a: DeadlockAccount, b: DeadlockAccount, n: Int) = a.synchronized {
    b.synchronized {
      a.money -= n
      b.money += n
    }
  }
  val a = new DeadlockAccount("Jack", 1000)
  val b = new DeadlockAccount("Jill", 2000)
  val t1 = thread { for (i <- 0 until 100) send(a, b, 1) }
  val t2 = thread { for (i <- 0 until 100) send(b, a, 1) }
  t1.join(); t2.join()
  log(s"a = ${a.money}, b = ${b.money}")

  import ThreadsCommunicate.getUniqueIdSynchronized
  class Account(val name: String, var money: Int) {
    val uid = getUniqueIdSynchronized()
  }

  def sendSync(a1: Account, a2: Account, n: Int) {
    def adjust {
      a1.money -= n
      a2.money += n
    }

    if (a1.uid < a2.uid)
      a1.synchronized { a2.synchronized { adjust } }
    else a2.synchronized { a1.synchronized { adjust } }
  }
}

object SynchronizedBadPool extends App {
  import Instantiator.log
  import scala.collection._

  private val tasks = mutable.Queue[() => Unit]()

  val worker = new Thread {
    def poll(): Option[() => Unit] = tasks.synchronized {
      if (tasks.nonEmpty) Some(tasks.dequeue()) else None
    }
    override def run(): Unit = while (true) poll() match {
      case Some(task) => task()
      case None       =>
    }
  }

  worker.setName("Worker")
  worker.setDaemon(true)
  worker.start()

  // call by name
  def asynchronous(body: => Unit) = tasks.synchronized {
    tasks.enqueue(() => body)
  }
  asynchronous(log("Hello"))
  asynchronous(log("World!"))
  Thread.sleep(500)
}

// Since greeter acquires the same monitor that the main thread previously released, the write to message by the main thread occurs before the check by the greeter thread. We thus know that the greeter thread will see the message.
object SynchronizedGuardedBlocks extends App {
  import Instantiator.{thread, log}

  val lock = new AnyRef
  var message: Option[String] = None
  val greeter = thread {
    lock.synchronized {
      while (message == None) lock.wait()
      log(message.get)
    }
  }
  lock.synchronized {
    message = Some("Hello!")
    lock.notify()
  }
  greeter.join()
}

object SynchronizedPool extends App {
  import Instantiator.log
  import scala.collection._

  private val tasks = mutable.Queue[() => Unit]()
  object Worker extends Thread {
    setDaemon(true)
    def poll() = tasks.synchronized {
      while (tasks.isEmpty) tasks.wait()
      tasks.dequeue()
    }
    override def run() = while (true) {
      val task = poll()
      task()
    }
  }
  Worker.start()
  def asynchronous(body: => Unit) = tasks.synchronized {
    tasks.enqueue(() => body)
    tasks.notify()
  }
  asynchronous { log("Hello ") }
  asynchronous { log("World!") }
  Thread.sleep(500)
}

object Worker extends Thread {
  import scala.collection._

  private val tasks = mutable.Queue[() => Unit]()
  var terminated = false

  def poll(): Option[() => Unit] = tasks.synchronized {
    while (tasks.isEmpty && !terminated) tasks.wait()
    if (!terminated) Some(tasks.dequeue()) else None
  }
  import scala.annotation.tailrec
  @tailrec override def run() = poll() match {
    case Some(task) => task(); run()
    case None       =>
  }
  def shutdown() = tasks.synchronized {
    terminated = true
    tasks.notify()
  }
}

// Exercises

/** Implement a parallel method, which takes two computation blocks a and b, and
  * starts each of them in a new thread. The method must return a tuple with the
  * result values of both the computations. It should have the following
  * signature:
  *
  * def parallel[A, B](a: =>A, b: =>B): (A, B)
  */

object Parallel extends App {
  import Instantiator.{thread, log}

  def parallel[A, B](a: => A, b: => B): (A, B) = {

    var aVal = null.asInstanceOf[A]
    var bVal = null.asInstanceOf[B]

    val t1 = thread {
      aVal = a
      log("aVal = " + aVal)
    }
    val t2 = thread {
      bVal = b
      log("bVal = " + bVal)
    }

    t1.join(); t2.join()

    (aVal, bVal)

  }

}

/** Implement a periodically method, which takes a time interval duration
  * specified in milliseconds, and a computation block b. The method starts a
  * thread that executes the computation block b every duration milliseconds. It
  * should have the following signature:
  *
  * def periodically(duration: Long)(b: => Unit): Unit
  */

object Periodically extends App {

  import Instantiator.{thread, log}

  def periodically(duration: Long)(b: => Unit): Unit = {
    val t1 = new Thread {
      while (true) {
        b
        Thread.sleep(duration)
      }
    }
    t1.setName("Periodically")
    t1.setDaemon(true)
    t1.start()
  }

  periodically(1000) {
    log("Hello")
  }

}

/** Implement a SyncVar class with the following interface:
  *
  * class SyncVar[T] { def get(): T = ??? def put(x: T): Unit = ??? }
  *
  * A SyncVar object is used to exchange values between two or more threads.
  * When created, the SyncVar object is empty:
  *
  *   - Calling get throws an exception
  *   - Calling put adds a value to the SyncVar object
  *
  * After a value is added to a SyncVar object, we can say that it is non-empty:
  *   - Calling get returns the current value, and changes the state to empty
  *   - Calling put throws an exception
  */

object SyncVar extends App {

  sealed trait SyncVar[T] {
    def get(): T
    def put(x: T): Unit
  }

  object SyncVarImpl {
    def make[T] = new SyncVar[T] { self =>
      private var empty: Boolean = true
      private var x: T = null.asInstanceOf[T]

      def get(): T = self.synchronized {
        if (empty) throw new Exception("Must not be empty")
        else {
          empty = true
          val v = x
          x = null.asInstanceOf[T]
          v
        }
      }
      def put(v: T): Unit = self.synchronized {
        if (!empty) throw new Exception("Must be empty")
        else {
          empty = false
          self.x = x
        }
      }
    }
  }

}

/** The SyncVar object from the previous exercise can be cumbersome to use, due
  * to exceptions when the SyncVar object is in an invalid state. Implement a
  * pair of methods `isEmpty` and `nonEmpty` on the SyncVar object. Then,
  * implement a producer thread that transfers a range of numbers 0 until 15 to
  * the consumer thread that prints them.
  */

object SyncVar2 extends App {
  import Instantiator.{thread, log}

  class SyncVar[T] { self =>
    private var empty: Boolean = true
    private var x: T = null.asInstanceOf[T]

    def isEmpty: Boolean = synchronized {
      empty
    }

    def nonEmpty: Boolean = synchronized {
      !empty
    }

    def getWait(): T = self.synchronized {
      while (empty) { self.wait() }

      empty = true
      self.notify()
      x
    }

    def putWait(v: T): Unit = self.synchronized {
      while (!empty) { self.wait() }
      empty = false
      self.x = v
      self.notify()
    }
  }

  val syncVar = new SyncVar[Int]

  val producer = thread {
    var x = 0
    while (x < 15) {
      syncVar.putWait(x)
      x = x + 1
    }
  }

  val consumer = thread {
    var x = -1
    while (x < 14) {
      x = syncVar.getWait
      log(s"get: $x")
    }
  }

  producer.join()
  consumer.join()
}

/** A SyncVar object can hold at most one value at a time. Implement a SyncQueue
  * class, which has the same interface as the SyncVar class, but can hold at
  * most n values. The parameter n is specified in the constructor of the
  * SyncQueue class.
  */

object SyncQueue extends App {
  import Instantiator.{thread, log}

  class SyncQueue[T](n: Int) { self =>

    import scala.collection.mutable.Queue

    private var queue = Queue[T]()

    def isEmpty: Boolean = synchronized {
      queue.isEmpty
    }

    def nonEmpty: Boolean = synchronized {
      !queue.isEmpty
    }

    def getWait(): T = self.synchronized {
      while (queue.isEmpty) { self.wait() }

      val v = queue.dequeue()
      self.notify()
      v
    }

    def putWait(v: T): Unit = self.synchronized {
      while (queue.size == n) { self.wait() }
      queue += v
      self.notify()
    }
  }

  val syncVar = new SyncQueue[Int](10)

  val producer = thread {
    var x = 0
    while (x < 15) {
      syncVar.putWait(x)
      x = x + 1
    }
  }

  val consumer = thread {
    var x = -1
    while (x < 14) {
      x = syncVar.getWait()
      log(s"get: $x")
    }
  }

  producer.join()
  consumer.join()

}
