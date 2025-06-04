class Parser(private val tokens: List<Token>) {
    private var pos = 0

    private fun current(): Token = tokens.getOrElse(pos) { tokens.last() }
    private fun advance() { pos++ }

    /**
     * Expects a function parameter in the form of (IDENTIFIER | NUMBER).
     */
    private fun expectFuncParams(): Pair<String, Boolean>? {
        if (current().type == TokenType.SEPARATOR && current().value == "(") {
            advance()
            if (current().type == TokenType.IDENTIFIER || current().type == TokenType.NUMBER) {
                val value = current().value
                val isNumber = current().type == TokenType.NUMBER
                advance()
                if (current().type == TokenType.SEPARATOR && current().value == ")") {
                    val closeParenToken = current()
                    advance()

                    // Check for a line break or EOF after the closing parenthesis
                    if (current().type != TokenType.ENDL && current().type != TokenType.EOF) {
                        throw IllegalArgumentException("SyntaxErr: No Linebreak after function! Error at: Row: ${closeParenToken.line}, Col: ${closeParenToken.column + closeParenToken.value.length}")
                    }
                    return Pair(value, isNumber)
                }
            }
        }
        return null
    }

    /**
     * Parses a return statement in the form of `return (IDENTIFIER | NUMBER)`.
     * Returns a ReturnNode if successful, or null if not a return statement.
     */
    fun parseReturn(): ReturnNode? {
        // Expects: return ( IDENTIFIER | NUMBER )
        if (current().type == TokenType.KEYWORD && current().value == "return") {
            //val returnToken = current()
            advance()
            val params = expectFuncParams()
            if (params != null) {
                return ReturnNode(params.first, params.second)
            }
        }
        return null
    }

    /**
     * Parses all statements in the input until EOF.
     * Returns a list of ASTNodes.
     */
    fun parseAll(): List<ASTNode> {
        val nodes = mutableListOf<ASTNode>()
        while (current().type != TokenType.EOF) {
            val node = parseReturn()
            if (node != null) {
                nodes.add(node)
            } else {
                advance()
            }
        }
        return nodes
    }
}
