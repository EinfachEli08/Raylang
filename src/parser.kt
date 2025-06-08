class Parser(private val tokens: List<Token>) {

    var knownFunctions: List<String> = emptyList()

    private var pos = 0

    private fun current(): Token = tokens.getOrElse(pos) { tokens.last() }
    private fun advance() { pos++ }


    /**
     * Parses a function definition in the form of `func name(params) { ... }` or `func name(params) => ...`.
     * Returns a FunctionNode if successful, or null if not a function definition.
     */
    fun parseFunction(): Function? {
        // Expects: func name(params) { ... } oder func name(params) => ...
        if (current().type == TokenType.KEYWORD && current().value == "func") {
            advance()
            if (current().type == TokenType.IDENTIFIER) {
                val funcName = current().value
                advance()
                // Parameter parsen
                val params = mutableListOf<String>()
                if (current().type == TokenType.SEPARATOR && current().value == "(") {
                    advance()
                    while (true) {
                        // Akzeptiere optionales 'var' vor Parametern
                        if (current().type == TokenType.KEYWORD && current().value == "var") {
                            advance()
                        }
                        if (current().type == TokenType.IDENTIFIER) {
                            params.add(current().value)
                            advance()
                            if (current().type == TokenType.SEPARATOR && current().value == ",") {
                                advance()
                                continue
                            } else {
                                break
                            }
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
                // Alternative Funktionssyntax: =>
                if (current().type == TokenType.OPERATOR && current().value == "=>") {
                    advance()
                    val body = mutableListOf<ASTNode>()
                    parse@ while (true) {
                        // Funktionscalls stacken mit ;
                        val functionCallNode = parseFunctionCall()
                        if (functionCallNode != null) {
                            body.add(functionCallNode)
                        }
                        // Beenden bei ENDL, Kommentar, Multiline-Kommentar, Raydoc-Kommentar
                        if (current().type == TokenType.ENDL || current().type == TokenType.EOF ||
                            current().type == TokenType.COMMENT || current().type == TokenType.OPEN_COMMENT) {
                            break@parse
                        }
                        // Bei ; weitermachen (stacked calls)
                        if (current().type == TokenType.SEPARATOR && current().value == ";") {
                            advance()
                            continue@parse
                        }
                        // Sonst: Token überspringen
                        advance()
                    }
                    knownFunctions += funcName
                    return Function(funcName, params, body)
                }
                // Klassische Funktionssyntax: {...}
                if (current().type == TokenType.SEPARATOR && current().value == "{") {
                    advance()
                    val body = mutableListOf<ASTNode>()
                    while (!(current().type == TokenType.SEPARATOR && current().value == "}")) {
                        // Reihenfolge: Extern, Function, Return, Exit, Variable, FunctionCall
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
                    return Function(funcName, params, body)
                } else {
                    throw IllegalArgumentException("SyntaxErr: Expected '{' or '=>' after function parameters at Row: ${current().line}, Col: ${current().column}")
                }
            }
        }
        return null
    }


    /**
     * Expects a function parameter in the form of (IDENTIFIER | NUMBER).
     */
    private fun parseFuncParams(): Pair<String, Boolean>? {
        if (current().type == TokenType.SEPARATOR && current().value == "(") {
            advance()
            // Leere Klammern erlauben
            if (current().type == TokenType.SEPARATOR && current().value == ")") {
                val closeParenToken = current()
                advance()
                // Check for a line break, EOF, Kommentar oder ; nach der schließenden Klammer
                if (current().type != TokenType.ENDL && current().type != TokenType.EOF && current().type != TokenType.COMMENT && current().type != TokenType.OPEN_COMMENT && !(current().type == TokenType.SEPARATOR && current().value == ";")) {
                    throw IllegalArgumentException("SyntaxErr: No Linebreak or ; after function! Error at: Row: ${closeParenToken.line}, Col: ${closeParenToken.column + closeParenToken.value.length}")
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
                    // Erlaube auch ; als Abschluss für gestapelte Funktionscalls
                    if (current().type != TokenType.ENDL && current().type != TokenType.EOF && current().type != TokenType.COMMENT && current().type != TokenType.OPEN_COMMENT && !(current().type == TokenType.SEPARATOR && current().value == ";")) {
                        throw IllegalArgumentException("SyntaxErr: No Linebreak or ; after function! Error at: Row: ${closeParenToken.line}, Col: ${closeParenToken.column + closeParenToken.value.length}")
                    }
                    return Pair(value, isNumber)
                }
            }
        }
        return null
    }


    /**
     * Parses a function call in the form of `IDENTIFIER(params)`.
     * Returns a FuncCall if successful, or null if not a function call.
     */
    fun parseFunctionCall(): FunctionCall? {
        if (current().type == TokenType.IDENTIFIER){
            val isKnown = knownFunctions.contains(current().value)
            if(isKnown) {
                val identifier = current().value
                advance()
                val params = parseFuncParams()
                if (params != null) {
                    return FunctionCall(identifier, params.first, params.second)
                }
            }else{
                throw IllegalArgumentException("DefErr: Function ´${current().value}´ isnt defined yet! \n      Error at Row: ${current().line}, Col: ${current().column}")
            }
        }
        return null
    }



    /**
     * Parses an extern statement in the form of `extern (IDENTIFIER | KEYWORD)`.
     * Returns an Extern if successful, or null if not an extern statement.
     */
    fun parseExtern(): Extern? {
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
            return Extern(functionList)
        }
        //TODO("Not yet implemented")
        return null
    }


    /**
     * Parses a return statement in the form of `return (IDENTIFIER | NUMBER)`.
     * Returns a ReturnNode if successful, or null if not a return statement.
     */
    fun parseReturn(): Return? {
        // Expects: return ( IDENTIFIER | NUMBER )
        if (current().type == TokenType.KEYWORD && current().value == "return") {
            advance()
            val params = parseFuncParams()
            if (params != null) {
                return Return(params.first, params.second)
            }
        }
        return null
    }


    /**
     * Parses a return statement in the form of `exit (IDENTIFIER | NUMBER)`.
     * Returns a ExitNode if successful, or null if not a return statement.
     */
    fun parseExit(): Exit? {
        // Expects: exit ( IDENTIFIER | NUMBER )
        if (current().type == TokenType.KEYWORD && current().value == "exit") {
            advance()
            val params = parseFuncParams()
            if (params != null) {
                return Exit(params.first, params.second)
            }
        }
        return null
    }


    /**
     * Parses all statements in the input until EOF.
     * Returns a list of ASTNodes.
     */
    fun parseAll(): List<ASTNode> {
        collectFunctionNames()
        val nodes = mutableListOf<ASTNode>()
        while (current().type != TokenType.EOF) {
            val node = parseExtern()
                ?: parseFunction()
                ?: parseFunctionCall()
                ?: parseReturn()
                ?: parseExit()
            if (node != null) {
                nodes.add(node)
            } else {
                advance()
            }
        }
        return nodes
    }


    /**
     * Collects all function names from the tokens.
     * This is done to ensure that function calls can be recognized even if they are defined later in the code.
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
}
