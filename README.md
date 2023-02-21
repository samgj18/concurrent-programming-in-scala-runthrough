# Concurrent Programming in Scala

## Preface

This is nothing but the runthrough of the book's content.

## [Chapter 1](lecture/ch1.md)

This chapter explains the basics of concurrent computing and presents some Scala preliminaries required for this book.
Specifically, it does the following:

- Shows a brief overview of concurrent programming
- Studies the advantages of using Scala when it comes to concurrency
- Covers the Scala preliminaries required for reading this book

## [Chapter 2](lecture/ch2.md)

This chapter not only explains the cornerstones of concurrency on the JVM, but also discuss how they interact with some
Scala-specific features. In particular, we will cover the following topics:

- Creating and starting threads and waiting for their completion
- Communication between threads using object monitors and the `synchronized` statement
- How to avoid busy-waiting using guarded blocks
- The semantics of volatile variables
- The specifics of the **Java Memory Model (JMM)**, and why the `JMM` is important
