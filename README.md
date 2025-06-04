# Ray Programming Language

<p align="center">
  <img src="ray.svg" alt="Ray mascot" width="200"/>
</p>

**Ray** is a modern, expressive programming language focused on readability, productivity, and safety — without sacrificing performance. It is currently in **active development**, and the syntax is evolving based on real-world experimentation and community feedback.

> ⚠️ Ray is a **prototype**. Things are subject to change.

---

## Why Ray?

Ray was designed from scratch with a few clear goals:

* **Clarity First:** Syntax that reads like natural language without being verbose.
* **Optionals Done Right:** Built-in support for nullable types and optional parameters.
* **Scoped Simplicity:** Clear rules for visibility and encapsulation (`scoped`, `val`, `var`).
* **Powerful Defaults:** Built-in tools like `match` or `when` streamline development.
* **Extension-Friendly:** Use `modify` to safely extend types like `String`.

---

## Features (Showcased)

✔ Nullable and default fields
✔ `record` types for data modeling
✔ Type-safe `match` and `when` expressions
✔ Inline function syntax
✔ Scoped visibility (`scoped`)
✔ String interpolation with `${}`
✔ Extension methods (`modify`)
✔ Built-in IO and Utillitiy Libraries

---
## Ray´s Syntax

| **Keyword / Symbol**        | **Description**                                                 | **Example**                                                   |
| --------------------------- | --------------------------------------------------------------- | ------------------------------------------------------------- |
| `// [text]`                 | Single-line comment                                             | `// This is a comment`                                        |
| `/* [text] */`              | Multi-line comment                                              | `/* Multi-line \n comment */`                                 |
| `package [name]`            | Defines the module/package the file belongs to                  | `package main`                                                |
| `import [pkg]`              | Imports classes, functions, or packages                         | `import ray.io`, `import ray.io.print()`                      |
| `record [Name] {}`          | Defines a data structure (like a `struct`)                      | `record Person { var name : String }`                         |
| `class [Name] {}`           | Defines a class                                                 | `class Greeter {}`                                            |
| `constructor(...) {}`       | Special method for initializing a class                         | `constructor(name : String) { ... }`                          |
| `func [name](...) : [type]` | Defines a function with parameters and return type              | `func greet(name : String) : String`                          |
| `scoped [type] [name]`      | Declares a variable/function with limited (local/private) scope | `scoped func greet(...)`                                      |
| `modify [Type] {}`          | Extension modifier to add methods to existing types             | `modify String { func upper() => ... }`                       |
| `var [name] : [type]`       | Mutable variable declaration                                    | `var age : Int = 28`                                          |
| `val [name] : [type]`       | Immutable (final) variable declaration                          | `val name : String = "Elias"`                                 |
| `[var]?`                    | Declares an optional (nullable) variable                        | `var age? : Int`                                              |
| `[var] = [value]`           | Assigns a value                                                 | `val x = 42`                                                  |
| `[var]? = [value]?`         | Assigns only if value is present; supports optional chaining    | `person.age? = age?`                                          |
| `==`, `!=`, `&&`            | Comparison and logical operators                                | `if (x == y && y != 0)`                                       |
| `{ ... }`                   | Code block                                                      | `func test() { println("Hi") }`                               |
| `=> [code]`                 | Inline/arrow function or expression                             | `func greet() => println("Hi")`                               |
| `return([value])`           | Returns a value from a function (must match declared type)      | `return("Hello")`                                             |
| `when ([var]) { ... }`      | Pattern matching (switch-case equivalent)                       | `when (role) { "admin" => ..., else => ... }`                 |
| `match ([var]) { ... }`     | Expression-based pattern match (returns value)                  | `val mood = match (input) { "happy" => ..., default => ... }` |
| `default`, `else`           | Default branch in `match` or `when` expressions                 | `default => "unknown"`                                        |
| `it`                        | Refers to the current instance (like `this` in other languages) | `it.toGreet.name = name`                                      |
| `toUpperCase()`             | Built-in string method                                          | `"hello".toUpperCase()`                                       |
| `.function()`               | Extension method call                                           | `"hello".shout()`                                             |

---

## Notes on Syntax Behavior

### Optional Handling

* Variables marked with `?` are nullable or optional:

  ```ray
  var name? : String
  name? = "Ray"  // valid
  name? = null   // also valid
  ```

### Inline Functions

* Concise syntax for single-expression functions:

  ```ray
  func shout(text : String) : String => return(text.toUpperCase() + "!!!")
  ```

### Match vs When

| **match**                    | **when**                         |
| ---------------------------- | -------------------------------- |
| Returns a value (expression) | Executes code blocks (statement) |
| Used in assignments          | Used in function bodies          |

---

## Sample Code

```ray
package main 
// This is the package name for the file. It is used to organize code into modules.

/*
    Importing the Ray library for I/O operations.
    imports:
        func print(input? : String) - for printing to the console
        func println(input? : String) - for printing with a newline
        func readLine(input? : String) - for reading user input
*/
import ray.io

/*
    Importing the Ray library for additional functionality.
    imports:
        func todo(message? : String, continue? : Bool) - for printing to the console
        func exit(code? : Int) - for exiting the program at any point
*/
import ray.utils

// imports can be written in a single line:
// import ray.io, ray.utils
// you can also import specific functions or classes from a package to minimize code size:
// import ray.io.print(), ray.utils.todo()

/* 
    Define a simple record (like a struct) to hold person data.
    age? means the field is optional (nullable).
    role has a default value of "guest", thus it can accept optional values without being optional.
*/ 
record Person {
    var name : String
    var age? : Int
    var role : String = "guest"
}

// Greeter class that stores a Person object and can greet them.
class Greeter {
    scoped var toGreet : Person  // Scoped = only accessible within this class.

    // a constructor that initializes the Greeter class with given parameters.
    constructor(name : String, age? : Int, role? : String) {

        // it is a special variable that refers to the current instance of the class.
        it.toGreet.name = name

        // If age is provided, set it. Otherwise, leave it as null.
        // Optional fields can be applied to optional fields.
        it.toGreet.age? = age?

        // an optional role can be set because person.role has a default value
        // If role is not provided, person.role defaults to "guest".
        it.toGreet.role = role?

        // Alternatively, this can be done:
        it.toGreet = Person(
            name = name,
            age = age?,
            role = role?      
        )
    }

    // Inline function to set the age.
    func setAge(age : Int) => it.toGreet.age = age

    // Inline function to set the role of the person (e.g., guest, admin).
    func setRole(role : String) => it.toGreet.role = role

    // Public function to say hi. Calls the internal greet() function.
    func sayHi() => println(greet(it.toGreet))

    // An internal (scoped) function that constructs a greeting message.
    scoped func greet(person : Person) : String {

        val intro = "Hello, I am ${person.name}"

        // If age is set, show it. Otherwise, show nothing.
        val ageText = if (person.age?) " and I am ${person.age} years old" else ""

        // Use a when expression (like switch-case) to modify the greeting based on role.
        val roleText = when (person.role) {
            "admin" => " [ADMIN]",
            "guest" => "",               // default role has no suffix
            else => " (${person.role})" // other roles are shown in parentheses
        }

        // Final message is built by combining all parts.
        return(intro + ageText + roleText)
    }

    func promote(role : String) {
        // The ToDo function is a placeholder for future implementation.
        // Its arguments are String as message and optional Bool for continuing after execution.
       todo("Implement role promotion logic")
    }
}

// This is a modifier method for the String type.
// Adds .upper() and .shout() functions to strings.
modify String {
    func upper() => return(it.toUpperCase())
    func shout() => return(it.toUpperCase() + "!!!")
}

// Main entry point with parameters for command line arguments.
func main(argv?, argc?) : Int {

    // Create a greeter for "Elias"
    var greeter = Greeter("Elias")
    greeter.setAge(28)
    greeter.setRole("admin")

    // Print greeting: "Hello, I am Elias and I am 28 years old [ADMIN]"
    greeter.sayHi()

    // the readLine function is used to read user input from the console.
    // its parameter is an optional prompt string.
    // it returns a string, even if the user enters a number.
    val mood = readLine("How are you? (happy/sad/other):")

    val moodComment : String = match (mood) {
        "happy" => "That's great!",
        "sad" => "Hope things get better.",
        default => "Mood unknown." // default is a wildcard (default)
    }

    // Call the shout() extension on the result: prints "THAT'S GREAT!!!"
    println(moodComment.shout())

    return(0)
}
```

---

## Getting Started

Ray isn't fully packaged yet, but you can explore the codebase and follow development:

```bash
git clone https://github.com/EinfachEli08/Raylang.git
cd Raylang
```

---

## Roadmap

* [x] Core syntax design
* [x] Initial parser
* [x] AST
* [x] First return
* [ ] Main Entry
* [ ] First hello world
* [ ] Functions
* [ ] ...
* [ ] Standard library expansion

## Maybe
* [ ] Type checker
* [ ] Bytecode compiler
* [ ] REPL support


---
## Current Progress
| Test                             | Status       |
|----------------------------------|--------------|
| 000: Returning values            | Passed       |
| 001: Comments                    | Not passed   |
| 002: imports                     | Not passed   |
| 003: Main entry point            | Not passed   |
| 004: Functions calling Functions | Doesnt Exist |
| XXX: More tests comming soon     | TBA          |
---

## Contributing

Ray is a community-driven experiment. Contributions are welcome!

* Open issues or ideas
* Submit pull requests for syntax refinements or tooling

---

## License

Ray is released under the [MIT License](LICENSE).

---

Let me know if you want a CLI usage example, REPL demo, or test framework section added.
