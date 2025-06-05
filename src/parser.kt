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
                    if (current().type != TokenType.ENDL && current().type != TokenType.EOF && current().type != TokenType.COMMENT && current().type != TokenType.OPEN_COMMENT) {
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
            advance()
            val params = expectFuncParams()
            if (params != null) {
                // Prüfe auf Inline-Kommentar (einzeilig oder mehrzeilig) direkt nach return(...)
                var inlineComment: String? = null
                if (current().type == TokenType.COMMENT && current().isMultiLine == false) {
                    advance()
                    if (current().type == TokenType.TEXT) {
                        inlineComment = current().value.trim()
                        advance()
                    }
                    if (current().type == TokenType.ENDL) {
                        advance()
                    }
                } else if (current().type == TokenType.OPEN_COMMENT && current().isMultiLine == true) {
                    // Mehrzeiliger Kommentar direkt nach return(...)
                    advance()
                    val lines = mutableListOf<String>()
                    while (current().type == TokenType.TEXT || current().type == TokenType.ENDL) {
                        if (current().type == TokenType.TEXT) {
                            lines.add(current().value.trim())
                        }
                        advance()
                    }
                    if (current().type == TokenType.CLOSED_COMMENT) {
                        advance()
                    }
                    inlineComment = lines.joinToString(" ")
                }
                return ReturnNode(params.first, params.second, inlineComment)
            }
        }
        return null
    }

    /**
     * Parses a comment in the form of `// comment` or `/* comment */`.
     * Returns a CommentNode if successful, or null if not a comment.
     */
    fun parseComment(): CommentNode? {
        // Expects: // comment oder /* comment */
        if (current().type == TokenType.COMMENT || current().type == TokenType.OPEN_COMMENT) {
            val startToken = current()
            val isMultiLine = startToken.isMultiLine == true
            advance()
            val lines = mutableListOf<String>()
            val commentText = StringBuilder()
            while (current().type == TokenType.TEXT || current().type == TokenType.ENDL) {
                if (current().type == TokenType.TEXT) {
                    commentText.append(current().value)
                }
                if (current().type == TokenType.ENDL && isMultiLine) {
                    lines.add(commentText.toString())
                    commentText.clear()
                }
                advance()
                if (!isMultiLine && current().type == TokenType.ENDL) {
                    advance()
                    break
                }
            }
            if (isMultiLine) {
                if (commentText.isNotEmpty()) {
                    lines.add(commentText.toString())
                }
                if (current().type == TokenType.CLOSED_COMMENT) {
                    advance()
                }
                return CommentNode(null, lines, true)
            } else {
                return CommentNode(commentText.toString(), null, false)
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
            val commentNode = parseComment()
            if (node != null) {
                nodes.add(node)
            } else if (commentNode != null) {
                nodes.add(commentNode)
            } else {
                advance()
            }
        }
        return nodes
    }
}
