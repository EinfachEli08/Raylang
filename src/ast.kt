sealed class ASTNode

// a Node for return-expressions like return(x) or return(42)
data class ReturnNode(val value: String, val isNumber: Boolean, val inlineComment: String? = null) : ASTNode()

// a Node for return-expressions like return(x) or return(42)
data class ExitNode(val value: String, val isNumber: Boolean, val inlineComment: String? = null) : ASTNode()

// a node for Comment lines, e.g. // this is a comment /* or this */
data class CommentNode(val text: String?, val lines:List<String>?, val isMultiLine: Boolean) : ASTNode()

