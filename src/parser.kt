class Parser(private val tokens: List<Token>) {

    private var knownFunctions: MutableSet<String> = mutableSetOf()
    private var pos = 0

    /**
     * Returns the current token without advancing the position.
     * @return The current token.
     */
    private fun current(): Token = tokens.getOrElse(pos) {
        tokens.last()
    }



    /**
     * Returns the next token without advancing the position.
     * @param offset The number of tokens to look ahead (default is 1).
     * @return The token at the specified offset.
     */
    private fun peek(offset: Int = 1): Token = tokens.getOrElse(pos + offset) {
        tokens.last()
    }



    /**
     * Advances to the next token and returns the current token.
     * @return The current token before advancing.
     */
    private fun advance(): Token = current().also {
        pos++
    }



    /**
     * Checks if the parser has reached the end of the token list.
     * @return True if the current token is EOF, false otherwise.
     */
    private fun isAtEnd(): Boolean = current().type == TokenType.EOF



    /**
     * Checks if the current token matches the specified type and optionally a value.
     * @param type The type of token to check against.
     * @param value Optional value to check against the current token's value.
     * @return True, if the current token matches the specified type and value, false otherwise.
     */
    private fun check(type: TokenType, value: String? = null): Boolean = current().type == type && (value == null || current().value == value)



    /**
     * Checks if the current token matches the specified type.
     * @param type The type of token to check against.
     * @param value Optional value to check against the current token's value.
     * @param errorMsg The error message to throw if the check fails.
     * @return True, if the current token matches the specified type, false otherwise.
     */
    private fun consume(type: TokenType, value: String? = null, errorMsg: String): Token {
        if (!check(type, value)) {
            throw IllegalArgumentException("$errorMsg at Row: ${current().line}, Col: ${current().column}")
        }
        return advance()
    }



    /**
     * Represents the context in which a function or statement is being parsed.
     * @param functionName The name of the function being parsed, if applicable.
     * @param parameters The list of parameters for the function, if applicable.
     * @param localVars The list of local variables defined within the function or context.
     */
    data class ParseContext(
        val functionName: String? = null,
        val parameters: List<String> = emptyList(),
        val localVars: MutableList<String> = mutableListOf()
    )


    /**
     * Parses the entire input and returns a list of AST nodes.
     * It collects function names first, then processes each top-level statement.
     * @return A list of AST nodes representing the parsed input.
     */
    fun parseAll(): List<ASTNode> {
        collectFunctionNames()
        val nodes = mutableListOf<ASTNode>()

        while (!isAtEnd()) {
            val node = parseTopLevelStatement()
            if (node != null) {
                nodes.add(node)
            } else {
                advance() // Skip unknown tokens
            }
        }
        return nodes
    }



    /**
     * Parses a top-level statement, which can be an extern, function definition, function call,
     * return statement, or exit statement.
     * @return An ASTNode representing the parsed statement, or null if no valid statement is found.
     */
    private fun parseTopLevelStatement(): ASTNode? {
        return when {
            check(TokenType.KEYWORD, "extern") -> parseExtern()
            check(TokenType.KEYWORD, "func") -> parseFunction()
            check(TokenType.IDENTIFIER) && knownFunctions.contains(current().value) -> parseFunctionCall(ParseContext())
            check(TokenType.KEYWORD, "return") -> parseReturn(ParseContext())
            check(TokenType.KEYWORD, "exit") -> parseExit(ParseContext())
            else -> null
        }
    }



    /**
     * Parses a function definition, which can be either an arrow function or a block function.
     * It expects the 'func' keyword, followed by the function name, parameters, and body.
     * @return A Function object representing the parsed function, or null if the current token is not a function definition.
     */
    fun parseFunction(): Function? {
        if (!check(TokenType.KEYWORD, "func")) return null

        advance() // consume 'func'
        val funcName = consume(TokenType.IDENTIFIER, null,"Expected function name").value
        val params = parseParameterList()
        val context = ParseContext(funcName, params)

        val body = when {
            check(TokenType.OPERATOR, "=>") -> parseArrowFunctionBody(context)
            check(TokenType.SEPARATOR, "{") -> parseBlockFunctionBody(context)
            else -> throw IllegalArgumentException(
                "Expected '{' or '=>' after function parameters at Row: ${current().line}, Col: ${current().column}"
            )
        }

        knownFunctions.add(funcName)
        return Function(funcName, params, body)
    }



    /**
     * Parses a list of parameters enclosed in parentheses.
     * It expects the opening parenthesis '(', followed by identifiers separated by commas, and ending with a closing parenthesis ')'.
     * @return A list of parameter names.
     */
    private fun parseParameterList(): List<String> {
        consume(TokenType.SEPARATOR, "(", "Expected '(' after function name")
        val params = mutableListOf<String>()

        while (!check(TokenType.SEPARATOR, ")")) {
            if (check(TokenType.IDENTIFIER)) {
                params.add(advance().value)

                if (check(TokenType.SEPARATOR, ",")) {
                    advance()
                } else if (!check(TokenType.SEPARATOR, ")")) {
                    throw IllegalArgumentException(
                        "Expected ',' or ')' in parameter list at Row: ${current().line}, Col: ${current().column}"
                    )
                }
            } else {
                throw IllegalArgumentException(
                    "Expected parameter name at Row: ${current().line}, Col: ${current().column}"
                )
            }
        }

        consume(TokenType.SEPARATOR, ")", "Expected ')' after parameters")
        return params
    }



    /**
     * Parses the body of an arrow function, which starts with '=>' and contains statements until a line end.
     * @param context The parsing context containing function name and parameters.
     * @return A list of AST nodes representing the function body.
     */
    private fun parseArrowFunctionBody(context: ParseContext): List<ASTNode> {
        advance() // consume '=>'
        val body = mutableListOf<ASTNode>()

        while (!isLineEnd()) {
            val stmt = parseStatement(context)
            if (stmt != null) {
                body.add(stmt)
            }

            if (check(TokenType.SEPARATOR, ";")) {
                advance()
            }
        }

        return body
    }



    /**
     * Parses the body of a block function, which starts with '{' and contains statements until '}'.
     * It also handles variable definitions by adding them to the local scope.
     * @param context The parsing context containing function name and parameters.
     * @return A list of AST nodes representing the function body.
     */
    private fun parseBlockFunctionBody(context: ParseContext): List<ASTNode> {
        advance() // consume '{'
        val body = mutableListOf<ASTNode>()

        while (!check(TokenType.SEPARATOR, "}")) {
            // Special handling for var declarations
            if (check(TokenType.KEYWORD, "var")) {
                val varDefs = parseVarDeclaration(context)
                body.addAll(varDefs)
                // Add all variable names to local scope
                varDefs.forEach { varDef ->
                    context.localVars.add(varDef.name)
                }
            } else {
                val stmt = parseStatement(context)
                if (stmt != null) {
                    body.add(stmt)
                } else {
                    advance() // Skip unknown tokens
                }
            }
        }

        consume(TokenType.SEPARATOR, "}", "Expected '}' to close function body")
        return body
    }



    /**
     * Parses a statement based on the current token type.
     * It can parse extern declarations, function calls, return statements, exit statements, and variable definitions.
     * @param context The parsing context containing function name and parameters.
     * @return An ASTNode representing the parsed statement, or null if no valid statement is found.
     */
    private fun parseStatement(context: ParseContext): ASTNode? {
        return when {
            check(TokenType.KEYWORD, "extern") -> parseExtern()
            check(TokenType.KEYWORD, "func") -> parseFunction()
            check(TokenType.KEYWORD, "return") -> parseReturn(context)
            check(TokenType.KEYWORD, "exit") -> parseExit(context)
            check(TokenType.KEYWORD, "var") -> {
                // Special case: var returns a list, we need to handle this differently
                // Option 1: Return only the first one (not ideal)
                // Option 2: Create a wrapper node
                // Option 3: Change parseStatement to return List<ASTNode>
                val varDefs = parseVarDeclaration(context)
                if (varDefs.isNotEmpty()) varDefs.first() else null
            }
            check(TokenType.IDENTIFIER) && knownFunctions.contains(current().value) -> parseFunctionCall(context)
            check(TokenType.IDENTIFIER) && peek().type == TokenType.OPERATOR && peek().value == "=" -> parseVariableAssign(context)
            else -> null
        }
    }



    /**
     * Parses an extern declaration, which allows the definition of external functions.
     * It expects the 'extern' keyword followed by a list of function names.
     * @return An Extern object containing the list of function names, or null if the current token is not an extern declaration.
     */
    fun parseExtern(): Extern? {
        if (!check(TokenType.KEYWORD, "extern")) return null

        advance() // consume 'extern'
        val functionList = mutableListOf<String>()

        while (check(TokenType.IDENTIFIER) ||
            (check(TokenType.KEYWORD) && !check(TokenType.KEYWORD, "extern"))) {

            functionList.add(advance().value)

            if (check(TokenType.SEPARATOR, ",")) {
                advance()
                if (!(check(TokenType.IDENTIFIER) ||
                            (check(TokenType.KEYWORD) && !check(TokenType.KEYWORD, "extern")))) {
                    throw IllegalArgumentException(
                        "Expected identifier after ',' in extern list at Row: ${current().line}, Col: ${current().column}"
                    )
                }
            } else if (check(TokenType.ENDL)) {
                advance()
                break
            } else {
                throw IllegalArgumentException(
                    "Expected ',' or newline after function name at Row: ${current().line}, Col: ${current().column}"
                )
            }
        }

        knownFunctions.addAll(functionList)
        return Extern(functionList)
    }



    /**
     * Parses a function call, which consists of an identifier followed by arguments in parentheses.
     * It checks if the function is known and returns a FunctionCall object.
     * @param context The parsing context containing function name and parameters.
     * @return A FunctionCall object representing the parsed function call, or null if the current token is not a function call.
     */
    fun parseFunctionCall(context: ParseContext): FunctionCall? {
        if (!check(TokenType.IDENTIFIER)) return null

        val funcName = current().value
        if (!knownFunctions.contains(funcName)) {
            throw IllegalArgumentException(
                "DefErr: Function '$funcName' isn't defined yet! Error at Row: ${current().line}, Col: ${current().column}"
            )
        }

        advance() // consume function name
        val args = parseArgumentsInParens(context,null,funcName)

        return FunctionCall(funcName, context.functionName ?: "", args)
    }



    /**
     * Parses a return statement, which consists of the 'return' keyword followed by an argument in parentheses.
     * It returns a Return object containing the function name and the argument.
     * @param context The parsing context containing function name and parameters.
     * @return A Return object representing the parsed return statement, or null if the current token is not a return statement.
     */
    fun parseReturn(context: ParseContext): Return? {
        if (!check(TokenType.KEYWORD, "return")) return null

        advance() // consume 'return'
        val arg = parseArgumentsInParens(context,1,"return").first()

        return Return(context.functionName ?: "", arg)
    }



    /**
     * Parses an exit statement, which consists of the 'exit' keyword followed by an argument in parentheses.
     * It returns an Exit object containing the function name and the argument.
     * @param context The parsing context containing function name and parameters.
     * @return An Exit object representing the parsed exit statement, or null if the current token is not an exit statement.
     */
    fun parseExit(context: ParseContext): Exit? {
        if (!check(TokenType.KEYWORD, "exit")) return null

        advance() // consume 'exit'
        val arg = parseArgumentsInParens(context,1,"exit").first()

        return Exit(context.functionName ?: "", arg)
    }



    /**
     * Parses variable declarations - ONLY declarations without assignments.
     * Examples: var x, var x, y, z
     * For assignments, those are handled separately as VariableAssign nodes.
     * @param context The parsing context containing function name and parameters.
     * @return An ASTNode representing the parsed variable declaration.
     */
    fun parseVarDeclaration(context: ParseContext): List<VariableDef> {
        if (!check(TokenType.KEYWORD, "var")) return emptyList()

        advance() // consume 'var'
        val varNames = mutableListOf<String>()

        varNames.add(consume(TokenType.IDENTIFIER, null, "Expected variable name after 'var'").value)

        // Check if there are more variable names (comma-separated)
        while (check(TokenType.SEPARATOR, ",")) {
            advance() // consume ','
            varNames.add(consume(TokenType.IDENTIFIER, null, "Expected variable name after ','").value)
        }

        // Check for duplicate declaration in the current scope (parameters and local variables)
        for (name in varNames) {
            if (context.parameters.contains(name) || context.localVars.contains(name)) {
                throw IllegalArgumentException("VarDefErr: Variable '$name' is already declared in the current scope! Error at Row: ${current().line}, Col: ${current().column}")
            }
        }

        if (check(TokenType.OPERATOR, "=")) {
            if (varNames.size > 1) {
                throw IllegalArgumentException("Cannot assign value to multiple variables in declaration at Row: ${current().line}, Col: ${current().column}")
            }

            advance() // consume '='

            // Check if this is a function call assignment
            if (check(TokenType.IDENTIFIER) && knownFunctions.contains(current().value)) {
                val nextToken = peek()
                if (nextToken.type == TokenType.SEPARATOR && nextToken.value == "(") {
                    // This is a function call assignment: var x = func(args)
                    val functionCall = parseFunctionCall(context)
                        ?: throw IllegalArgumentException("Failed to parse function call")

                    // Add the variable to the local scope
                    context.localVars.add(varNames[0])

                    // Note: We return a FunctionCallAssign wrapped in a list here
                    // You might need to handle this differently depending on your AST structure
                    return listOf(FunctionCallAssign(context.functionName ?: "", varNames[0], functionCall) as VariableDef)
                }
            }

            // Regular assignment: var x = 42
            val arg = parseArgument(context)
                ?: throw IllegalArgumentException(
                    "Expected value after '=' in var definition at Row: ${current().line}, Col: ${current().column}"
                )
            return listOf(VariableDef(context.functionName ?: "", varNames[0], arg))
        } else {
            // Variable declaration(s) without assignment - initialize all to 0
            return varNames.map { name ->
                VariableDef(context.functionName ?: "", name, Arg.Literal(0))
            }
        }
    }



    /**
     * Parses a variable assignment statement (not declaration).
     * Example: x = 42, x = getE()
     * @param context The parsing context containing function name and parameters.
     * @return A VariableAssign or FunctionCallAssign object representing the parsed assignment.
     */
    fun parseVariableAssign(context: ParseContext): ASTNode? {
        if (!check(TokenType.IDENTIFIER)) return null

        val varName = advance().value // consume variable name
        consume(TokenType.OPERATOR, "=", "Expected '=' in assignment")

        // Check if this is a function call assignment
        if (check(TokenType.IDENTIFIER) && knownFunctions.contains(current().value)) {
            val nextToken = peek()
            if (nextToken.type == TokenType.SEPARATOR && nextToken.value == "(") {
                // This is a function call assignment: x = func(args)
                val functionCall = parseFunctionCall(context)
                    ?: throw IllegalArgumentException("Failed to parse function call")
                return FunctionCallAssign(context.functionName ?: "", varName, functionCall)
            }
        }

        // Regular assignment: x = 42
        val arg = parseArgument(context)
            ?: throw IllegalArgumentException(
                "Expected value after '=' in assignment at Row: ${current().line}, Col: ${current().column}"
            )

        return VariableAssign(context.functionName ?: "", varName, arg)
    }



    /**
     * Parses argument lists in parentheses, optionally supporting a limit for the number of arguments.
     * If no limit is set, unlimited arguments are allowed.
     * @param context The parsing context with function names and parameters.
     * @param limit Maximum number of arguments (optional).
     * @param functionName The name of the function being parsed (for error messages).
     * @return A list of Arg objects (if limit == 1: list with one Arg or empty/Bogus).
     */
    private fun parseArgumentsInParens(context: ParseContext, limit: Int? = null, functionName: String?): List<Arg> {
        consume(TokenType.SEPARATOR, "(", "Expected '('")

        val args = mutableListOf<Arg>()
        var count = 0

        if (!check(TokenType.SEPARATOR, ")")) {
            do {
                if (limit != null && count >= limit) {
                    throw IllegalArgumentException(
                        "DEFERR: Too many arguments ${if (!functionName.isNullOrEmpty()) "in $functionName " else ""}(max $limit) at Row: ${current().line}, Col: ${current().column}"
                    )
                }
                val arg = parseArgument(context)
                    ?: throw IllegalArgumentException(
                        "Expected argument at Row: ${current().line}, Col: ${current().column}"
                    )
                args.add(arg)
                count++
            } while (check(TokenType.SEPARATOR, ",") && advance().let { true })
        }

        consume(TokenType.SEPARATOR, ")", "Expected ')' after arguments")
        expectLineEndOrSemicolon()

        if (limit == 1) {
            return if (args.isEmpty()) listOf(Arg.Bogus) else args
        }

        return args
    }



    /**
     * Parses an argument, which can be a number or an identifier.
     * It returns an Arg object representing the parsed argument.
     * @param context The parsing context containing function name and parameters.
     * @return An Arg object representing the parsed argument, or null if no valid argument is found.
     */
    private fun parseArgument(context: ParseContext): Arg? {
        return when (current().type) {
            TokenType.NUMBER -> {
                val value = advance().value
                Arg.Literal(value.toLong())
            }
            TokenType.IDENTIFIER -> {
                val name = advance().value
                resolveVariable(name, context)
            }
            else -> null
        }
    }



    /**
     * Resolves a variable name to its corresponding Arg object.
     * It checks if the name is a function parameter or a local variable in the current context.
     * @param name The name of the variable to resolve.
     * @param context The parsing context containing function parameters and local variables.
     * @return An Arg object representing the resolved variable.
     * @throws IllegalArgumentException if the name is not a valid function parameter or local variable.
     */
    private fun resolveVariable(name: String, context: ParseContext): Arg {
        val paramIdx = context.parameters.indexOf(name)
        if (paramIdx != -1) {
            return Arg.AutoVar(paramIdx)
        }

        val localIdx = context.localVars.indexOf(name)
        if (localIdx != -1) {
            return Arg.AutoVar(localIdx + context.parameters.size)
        }

        throw IllegalArgumentException("NameErr: '$name' is not a valid function parameter or local variable!")
    }



    /**
     * Checks if the current token is a line end, which can be an ENDL, EOF, COMMENT, or OPEN_COMMENT.
     * @return True if the current token is a line end, false otherwise.
     */
    private fun isLineEnd(): Boolean {
        return check(TokenType.ENDL) ||
                check(TokenType.EOF) ||
                check(TokenType.COMMENT) ||
                check(TokenType.OPEN_COMMENT)
    }



    /**
     * Checks if the current token is a line end or a semicolon.
     * If not, it throws an IllegalArgumentException with the current token's position.
     * @throws IllegalArgumentException if the current token is not a line end or semicolon.
     */
    private fun expectLineEndOrSemicolon() {
        if (!isLineEnd() && !check(TokenType.SEPARATOR, ";")) {
            throw IllegalArgumentException(
                "Expected line break or ';' at Row: ${current().line}, Col: ${current().column + current().value.length}"
            )
        }
    }



    /**
     * Collects all known function names from the tokens.
     * It scans through the tokens and adds function names to the knownFunctions set.
     */
    fun collectFunctionNames() {
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]

            when {
                token.type == TokenType.KEYWORD && token.value == "func" -> {
                    val nameToken = tokens.getOrNull(i + 1)
                    if (nameToken?.type == TokenType.IDENTIFIER) {
                        knownFunctions.add(nameToken.value)
                    }
                }
                token.type == TokenType.KEYWORD && token.value == "extern" -> {
                    var j = i + 1
                    while (j < tokens.size &&
                        (tokens[j].type == TokenType.IDENTIFIER ||
                                (tokens[j].type == TokenType.KEYWORD && tokens[j].value != "extern"))) {
                        knownFunctions.add(tokens[j].value)
                        j++
                    }
                }
            }
            i++
        }
    }
}