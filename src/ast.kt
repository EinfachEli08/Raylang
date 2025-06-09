sealed class ASTNode

// a Node for return-expressions like return(x) or return(42)
data class Return(val value: String, val isNumber: Boolean) : ASTNode()

// a Node for exit-expressions like exit(x : Int) or exit(42)
data class Exit(val value: String, val isNumber: Boolean) : ASTNode()



// a node for extern lines, e.g. extern putchar
data class Extern(val functionList:List<String>) : ASTNode()



// a node for function calls, e.g. putchar(x: Any) or putchar("A")
data class FunctionCall(val name: String, val value: String, val isNumber: Boolean) : ASTNode()

// a node for function definitions, e.g. func foo(x: Int) { ... }
data class Function(val name: String, val params: List<String>, val body: List<ASTNode>) : ASTNode()



// a Node for variable definitions, e.g. var x = 42
data class VariableDef(val scope:String, val name: String, val value: String, val valueIsNumber: Boolean) : ASTNode()