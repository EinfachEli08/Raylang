sealed class ASTNode

// a Node for return-expressions like return(x) or return(42)
data class ReturnNode(val value: String, val isNumber: Boolean) : ASTNode()

// a Node for exit-expressions like exit(x : Int) or exit(42)
data class ExitNode(val value: String, val isNumber: Boolean) : ASTNode()

// a node for extern lines, e.g. extern putchar
data class ExternNode(val functionList:List<String>) : ASTNode()

// a node for function calls, e.g. putchar(x: Any) or putchar("A")
data class FunctionCallNode(val name: String, val value: String, val isNumber: Boolean) : ASTNode()

