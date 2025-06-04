sealed class ASTNode

// a Node for return-expressions like return(x) or return(42)
data class ReturnNode(val value: String, val isNumber: Boolean) : ASTNode()
