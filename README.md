# Concurrent Programming in Scala

## Preface

This is nothing but the runthrough of the book's content.

## [Chapter 1](https://samuelgomez.co/posts/concurrent-programming-in-scala-part-1/)

This chapter explains the basics of concurrent computing and presents some Scala preliminaries required for this book.
Specifically, it does the following:

- Shows a brief overview of concurrent programming
- Studies the advantages of using Scala when it comes to concurrency
- Covers the Scala preliminaries required for reading this book

## [Chapter 2](https://samuelgomez.co/posts/concurrent-programming-in-scala-part-2/)

This chapter not only explains the cornerstones of concurrency on the JVM, but also discuss how they interact with some
Scala-specific features. In particular, we will cover the following topics:

- Creating and starting threads and waiting for their completion
- Communication between threads using object monitors and the `synchronized` statement
- How to avoid busy-waiting using guarded blocks
- The semantics of volatile variables
- The specifics of the **Java Memory Model (JMM)**, and why the `JMM` is important

## [Chapter 3](https://samuelgomez.co/posts/concurrent-programming-in-scala-part-3/)

This chapter presents the traditional building blocks of concurrent programs in Scala. Also covers the following:

- Using Executor objects to run concurrent computations
- Using atomic primitives to atomically switch between different states in the program and implement locks and lock-free algorithms.
- The implementation of lazy values and their impact on concurrent programs
- Some important classes of concurrent collections and learned how to apply them in practice
- Learn how to use some features from the scala.sys.process package
