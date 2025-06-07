class Parser(private val tokens: List<Token>) {
    var knownFunctions: List<String> = emptyList()
    private var pos = 0

    private fun current(): Token = tokens.getOrElse(pos) { tokens.last() }
    private fun advance() { pos++ }


    /**
     * Parses an inline comment (single-line or multi-line) directly after an expression.
     * Returns the comment text or null if no comment is present.
     */
    private fun parseInlineComment(): String? {
        if (current().type == TokenType.COMMENT && current().isMultiLine == false) {
            advance()
            if (current().type == TokenType.TEXT) {
                val comment = current().value.trim()
                advance()
                if (current().type == TokenType.ENDL) {
                    advance()
                }
                return comment
            }
            if (current().type == TokenType.ENDL) {
                advance()
            }
            return null
        } else if (current().type == TokenType.OPEN_COMMENT && current().isMultiLine == true) {
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
            return lines.joinToString(" ")
        }
        return null
    }



    /**
     * Expects a function parameter in the form of (IDENTIFIER | NUMBER).
     */
    private fun expectFuncParams(): Pair<String, Boolean>? {
        if (current().type == TokenType.SEPARATOR && current().value == "(") {
            advance()
            // Leere Klammern erlauben
            if (current().type == TokenType.SEPARATOR && current().value == ")") {
                val closeParenToken = current()
                advance()
                // Check for a line break oder EOF nach der schließenden Klammer
                if (current().type != TokenType.ENDL && current().type != TokenType.EOF && current().type != TokenType.COMMENT && current().type != TokenType.OPEN_COMMENT) {
                    throw IllegalArgumentException("SyntaxErr: No Linebreak after function! Error at: Row: ${closeParenToken.line}, Col: ${closeParenToken.column + closeParenToken.value.length}")
                }
                return Pair("", false)
            }
            if (current().type == TokenType.IDENTIFIER || current().type == TokenType.NUMBER) {
                val value = current().value
                val isNumber = current().type == TokenType.NUMBER
                advance()
                if (current().type == TokenType.SEPARATOR && current().value == ")") {
                    val closeParenToken = current()
                    advance()
                    if (current().type != TokenType.ENDL && current().type != TokenType.EOF && current().type != TokenType.COMMENT && current().type != TokenType.OPEN_COMMENT) {
                        throw IllegalArgumentException("SyntaxErr: No Linebreak after function! Error at: Row: ${closeParenToken.line}, Col: ${closeParenToken.column + closeParenToken.value.length}")
                    }
                    return Pair(value, isNumber)
                }
            }
        }
        return null
    }

    fun parseExtern(): ExternNode? {
        // Expects: extern (IDENTIFIER | KEYWORD)
        if (current().type == TokenType.KEYWORD && current().value == "extern") {
            advance()

            val functionList = mutableListOf<String>()
            // Akzeptiere IDENTIFIER oder KEYWORD als Funktionsnamen
            while (current().type == TokenType.IDENTIFIER || (current().type == TokenType.KEYWORD && current().value != "extern")) {
                functionList.add(current().value)
                advance()

                if (current().type == TokenType.SEPARATOR && current().value == ",") {
                    advance()
                    if (!(current().type == TokenType.IDENTIFIER || (current().type == TokenType.KEYWORD && current().value != "extern"))) {
                        throw IllegalArgumentException("SyntaxErr: Expected identifier after ',' in extern list. \n      Error at: Row: ${current().line}, Col: ${current().column}")
                    }
                } else if (current().type == TokenType.ENDL && current().value == "ENDL") {
                    advance()
                    break
                } else {
                    throw IllegalArgumentException("SyntaxErr: Expected ',' or ')' after function name. \n      Error at: Row: ${current().line}, Col: ${current().column}")
                }
            }
            knownFunctions += functionList // Update known functions
            return ExternNode(functionList)
        }
        //TODO("Not yet implemented")
        return null
    }

    fun parseFunctionCall(): FunctionCallNode? {
        if (current().type == TokenType.IDENTIFIER){
            //println(knownFunctions)
            val isKnown = knownFunctions.contains(current().value)
            if(isKnown) {
                val identifier = current().value
                //println(current())
                advance()
                val params = expectFuncParams()
                if (params != null) {
                    return FunctionCallNode(identifier, params.first, params.second)
                }
            }else{
                throw IllegalArgumentException("DefErr: Function ´${current().value}´ isnt defined yet! \n      Error at Row: ${current().line}, Col: ${current().column}")
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
                return ReturnNode(params.first, params.second)
            }
        }
        return null
    }

    /**
     * Parses a return statement in the form of `exit (IDENTIFIER | NUMBER)`.
     * Returns a ExitNode if successful, or null if not a return statement.
     */
    fun parseExit(): ExitNode? {
        // Expects: exit ( IDENTIFIER | NUMBER )
        if (current().type == TokenType.KEYWORD && current().value == "exit") {
            advance()
            val params = expectFuncParams()
            if (params != null) {
                val inlineComment = parseInlineComment()
                return ExitNode(params.first, params.second)
            }
        }
        return null
    }

    /**
     * Parses a comment in the form of `// comment` or `/* comment */`.
     * Returns a CommentNode if successful, or null if not a comment.


    REMOVED: Comments usually dont get parsed into the AST, but are used for documentation or debugging.
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
*/
    fun collectFunctionNames() {
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.type == TokenType.KEYWORD && t.value == "func") {
                // nächstes Token ist Funktionsname
                val nameToken = tokens.getOrNull(i + 1)
                if (nameToken != null && nameToken.type == TokenType.IDENTIFIER) {
                    knownFunctions += nameToken.value
                }
            }
            if (t.type == TokenType.KEYWORD && t.value == "extern") {
                // alle folgenden IDENTIFIER oder KEYWORD bis Zeilenende
                var j = i + 1
                while (j < tokens.size && (tokens[j].type == TokenType.IDENTIFIER || (tokens[j].type == TokenType.KEYWORD && tokens[j].value != "extern"))) {
                    knownFunctions += tokens[j].value
                    j++
                }
            }
            i++
        }
    }

    /**
     * Parses all statements in the input until EOF.
     * Returns a list of ASTNodes.
     */
    fun parseAll(): List<ASTNode> {
        collectFunctionNames() // Funktionsnamen vorab sammeln!
        val nodes = mutableListOf<ASTNode>()
        while (current().type != TokenType.EOF) {
            val externNode = parseExtern()
            val functionNode = parseFunction()
            val returnNode = parseReturn()
            val exitNode = parseExit()
            val functionCallNode = parseFunctionCall()

            if (externNode != null) {
                nodes.add(externNode)
            } else if (functionNode != null) {
                nodes.add(functionNode)
            } else if (functionCallNode != null) {
                nodes.add(functionCallNode)
            } else if (returnNode != null) {
                nodes.add(returnNode)
            } else if (exitNode != null) {
                nodes.add(exitNode)
            } else{
                advance()
            }
        }
        return nodes
    }

    fun parseFunction(): FunctionNode? {
        // Expects: func name(params) { ... }
        if (current().type == TokenType.KEYWORD && current().value == "func") {
            advance()
            if (current().type == TokenType.IDENTIFIER) {
                val funcName = current().value
                advance()
                // Parameter parsen
                val params = mutableListOf<String>()
                if (current().type == TokenType.SEPARATOR && current().value == "(") {
                    advance()
                    while (current().type == TokenType.IDENTIFIER) {
                        params.add(current().value)
                        advance()
                        if (current().type == TokenType.SEPARATOR && current().value == ",") {
                            advance()
                        } else {
                            break
                        }
                    }
                    if (current().type == TokenType.SEPARATOR && current().value == ")") {
                        advance()
                    } else {
                        throw IllegalArgumentException("SyntaxErr: Expected ')' after function parameters at Row: ${current().line}, Col: ${current().column}")
                    }
                } else {
                    throw IllegalArgumentException("SyntaxErr: Expected '(' after function name at Row: ${current().line}, Col: ${current().column}")
                }
                // Funktionsrumpf parsen
                if (current().type == TokenType.SEPARATOR && current().value == "{") {
                    advance()
                    val body = mutableListOf<ASTNode>()
                    while (!(current().type == TokenType.SEPARATOR && current().value == "}")) {
                        // Reihenfolge: Extern, Function, Return, Exit, FunctionCall
                        val externNode = parseExtern()
                        val functionNode = parseFunction()
                        val returnNode = parseReturn()
                        val exitNode = parseExit()
                        val functionCallNode = parseFunctionCall()
                        if (externNode != null) {
                            body.add(externNode)
                        } else if (functionNode != null) {
                            body.add(functionNode)
                        } else if (functionCallNode != null) {
                            body.add(functionCallNode)
                        } else if (returnNode != null) {
                            body.add(returnNode)
                        } else if (exitNode != null) {
                            body.add(exitNode)
                        } else {
                            advance()
                        }
                    }
                    advance() // skip '}'
                    knownFunctions += funcName
                    return FunctionNode(funcName, params, body)
                } else {
                    throw IllegalArgumentException("SyntaxErr: Expected '{' or '=>' after function parameters at Row: ${current().line}, Col: ${current().column}")
                }
            }
        }
        return null
    }
}
