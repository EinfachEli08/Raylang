sealed class ASTNode

// a Node for return-expressions like return(x) or return(42)
data class Return(val scope: String, val arg: Arg) : ASTNode()

// a Node for exit-expressions like exit(x) or exit(42)
data class Exit(val scope: String, val arg: Arg) : ASTNode()

// a node for extern lines, e.g. extern putchar
data class Extern(val functionList: List<String>) : ASTNode()

// a node for function calls, e.g. putchar(x) or putchar("A") - now supports multiple arguments
data class FunctionCall(val name: String, val scope: String, val args: List<Arg>) : ASTNode()

// a node for function definitions, e.g. func foo(x) { ... }
data class Function(val name: String, val params: List<String>, val body: List<ASTNode>) : ASTNode()




// a Node for variable definitions, e.g. var x = 42
data class VariableDef(val scope: String, val name: String, val arg: Arg) : ASTNode()

// a Node for multiple variable declarations, e.g. var x, y, z
data class MultiVariableDef(val scope: String, val names: List<String>) : ASTNode()

// a Node for variable assignments, e.g. x = 42
data class VariableAssign(val scope: String, val name: String, val arg: Arg) : ASTNode()




sealed class Arg {
    data class Deref(val index: Int) : Arg()
    data class RefAutoVar(val index: Int) : Arg()
    data class RefExternal(val name: String) : Arg()
    data class External(val name: String) : Arg()
    data class AutoVar(val index: Int) : Arg()
    data class Literal(val value: Long) : Arg()
    data class DataOffset(val offset: Int) : Arg()
    object Bogus : Arg()
}