// Equality
val newString = new String("Hello")
val anotherString = new String("Hello")

// Reference equality
newString eq anotherString // false

/** Two values a and b are equal, a == b , whenever they have equal contents,
  * regardless of the physical representation, or identity, of those values in
  * memory
  */
// Structural equality
newString == anotherString // true

// Equals and HashCode note:
//
// Unless we define a case class where hashcode() and equals() are already appropriately defined for us, we need to override them, otherwise
// equality might not work as expected.

class Person(val name: String, val age: Int)

val p1 = new Person("John", 30)
val p2 = new Person("John", 30)

p1 == p2 // false <- this is because we haven't overridden equals() and hashcode() methods

// We can override equals() and hashcode() methods as follows:
class PersonOverride(val name: String, val age: Int) {
  override def equals(other: Any): Boolean = other match {
    case person: PersonOverride =>
      this.name == person.name && this.age == person.age
    case _ => false
  }

  override def hashCode(): Int =
    if (name eq null) age else name.hashCode + 31 * age
}

// Or simply:
case class PersonCaseClass(val name: String, val age: Int)

val p1c = new PersonCaseClass("John", 30)
val p2c = new PersonCaseClass("John", 30)

p1c == p2c // true

val p3o = new PersonOverride("John", 30)
val p4o = new PersonOverride("John", 30)

p3o == p4o // true
