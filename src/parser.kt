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
                        val functionCallNode = parseFunctionCall(funcName)
                        if (functionCallNode != null) {
                            body.add(functionCallNode)
                        }
                        if (current().type == TokenType.ENDL || current().type == TokenType.EOF ||
                            current().type == TokenType.COMMENT || current().type == TokenType.OPEN_COMMENT) {
                            break@parse
                        }
                        if (current().type == TokenType.SEPARATOR && current().value == ";") {
                            advance()
                            continue@parse
                        }
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
                        val externNode = parseExtern()
                        val functionNode = parseFunction()
                        val returnNode = parseReturn(funcName)
                        val exitNode = parseExit(funcName)
                        val varDefNode = parseVarDef(funcName)
                        val functionCallNode = parseFunctionCall(funcName)
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
                        } else if (varDefNode != null) {
                            body.add(varDefNode)
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
    private fun parseFuncParams(): Arg? {
        if (current().type == TokenType.SEPARATOR && current().value == "(") {
            advance()
            // Leere Klammern erlauben
            if (current().type == TokenType.SEPARATOR && current().value == ")") {
                advance()
                // Check for a line break, EOF, Kommentar oder ; nach der schließenden Klammer
                if (current().type != TokenType.ENDL && current().type != TokenType.EOF && current().type != TokenType.COMMENT && current().type != TokenType.OPEN_COMMENT && !(current().type == TokenType.SEPARATOR && current().value == ";")) {
                    throw IllegalArgumentException("SyntaxErr: No Linebreak or ; after function! Error at: Row: ${current().line}, Col: ${current().column + current().value.length}")
                }
                return Arg.Bogus
            }
            if (current().type == TokenType.IDENTIFIER) {
                val value = current().value
                advance()
                if (current().type == TokenType.SEPARATOR && current().value == ")") {
                    advance()
                    if (current().type != TokenType.ENDL && current().type != TokenType.EOF && current().type != TokenType.COMMENT && current().type != TokenType.OPEN_COMMENT && !(current().type == TokenType.SEPARATOR && current().value == ";")) {
                        throw IllegalArgumentException("SyntaxErr: No Linebreak or ; after function! Error at: Row: ${current().line}, Col: ${current().column + current().value.length}")
                    }
                    return Arg.RefAutoVar(0) // Platzhalter: Index 0, ggf. anpassen
                }
            } else if (current().type == TokenType.NUMBER) {
                val value = current().value
                advance()
                if (current().type == TokenType.SEPARATOR && current().value == ")") {
                    advance()
                    if (current().type != TokenType.ENDL && current().type != TokenType.EOF && current().type != TokenType.COMMENT && current().type != TokenType.OPEN_COMMENT && !(current().type == TokenType.SEPARATOR && current().value == ";")) {
                        throw IllegalArgumentException("SyntaxErr: No Linebreak or ; after function! Error at: Row: ${current().line}, Col: ${current().column + current().value.length}")
                    }
                    return Arg.Literal(value.toLong())
                }
            }
        }
        return null
    }


    /**
     * Parses a function call in the form of `IDENTIFIER(params)`.
     * Returns a FuncCall if successful, or null if not a function call.
     */
    fun parseFunctionCall(scope: String?): FunctionCall? {
        if (current().type == TokenType.IDENTIFIER){
            val isKnown = knownFunctions.contains(current().value)
            if(isKnown) {
                val identifier = current().value
                advance()
                val params = parseFuncParams()
                if (params != null) {
                    //TODO: scope handling
                    return FunctionCall(identifier, scope?:"",params)
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
    fun parseReturn(scope: String?): Return? {
        // Expects: return ( IDENTIFIER | NUMBER )
        if (current().type == TokenType.KEYWORD && current().value == "return") {
            advance()
            val params = parseFuncParams()
            if (params != null) {
                //TODO: scope handling
                return Return(scope?:"", params)
            }
        }
        return null
    }


    /**
     * Parses a return statement in the form of `exit (IDENTIFIER | NUMBER)`.
     * Returns a ExitNode if successful, or null if not a return statement.
     */
    fun parseExit(scope: String?): Exit? {
        // Expects: exit ( IDENTIFIER | NUMBER )
        if (current().type == TokenType.KEYWORD && current().value == "exit") {
            advance()
            val params = parseFuncParams()
            if (params != null) {
                //TODO: scope handling
                return Exit(scope?:"",params)
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
                ?: parseFunctionCall(null)
                ?: parseReturn(null)
                ?: parseExit(null)
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

    /**
     * Parst eine Variablendefinition: var name = value
     */
    fun parseVarDef(functionScope: String): VariableDef? {
        if (current().type == TokenType.KEYWORD && current().value == "var") {
            advance()
            if (current().type == TokenType.IDENTIFIER) {
                val varName = current().value
                advance()
                if (current().type == TokenType.OPERATOR && current().value == "=") {
                    advance()
                    // Nutze parseFuncParams-Logik für den rechten Wert
                    val arg = when {
                        current().type == TokenType.NUMBER -> {
                            val value = current().value
                            advance()
                            Arg.Literal(value.toLong())
                        }
                        current().type == TokenType.IDENTIFIER -> {
                            val value = current().value
                            advance()
                            Arg.RefAutoVar(0) // Platzhalter, ggf. anpassen
                        }
                        else -> throw IllegalArgumentException("SyntaxErr: Expected value after '=' in var definition at Row: ${current().line}, Col: ${current().column}")
                    }
                    return VariableDef(functionScope, varName, arg)
                } else {
                    throw IllegalArgumentException("SyntaxErr: Expected '=' after variable name in var definition at Row: ${current().line}, Col: ${current().column}")
                }
            } else {
                throw IllegalArgumentException("SyntaxErr: Expected variable name after 'var' at Row: ${current().line}, Col: ${current().column}")
            }
        }
        return null
    }

}