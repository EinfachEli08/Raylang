class Codegen(private val nodes:List<ASTNode>) {

    var externalList: MutableList<String> = mutableListOf()
    var varNodes: MutableList<VariableDef> = mutableListOf()

    /**
     * Generates the assembly code for the given AST nodes.
     * The output is a StringBuilder containing the assembly code.
     */
    fun generateProgram(): StringBuilder {
        val output = StringBuilder()
        output.appendLine("format ELF64")
        output.appendLine("")

        generateExternSection(output, nodes)
        generateDataSection(output, nodes)
        generateFunctions(output, nodes)


        return output
    }

    /**
     * Sammelt alle Variablennamen aus VarDef-Nodes und deklariert sie im .bss-Abschnitt.
     */
    fun generateDataSection(output: StringBuilder, nodes: List<ASTNode>) {
        varNodes = nodes.filterIsInstance<VariableDef>().toMutableList()
        // Suche auch in Funktionen nach lokalen Variablen
        for (node in nodes) {
            if (node is Function) {
                val funcName = node.name
                for (stmt in node.body) {
                    if (stmt is VariableDef) {
                        varNodes.add(VariableDef(funcName, stmt.name, stmt.value, stmt.isNumber))
                    }
                }
            }
        }
        if (varNodes.isNotEmpty()) {
            output.appendLine("section '.data' writable")
            for (varDef in varNodes) {
                output.appendLine("    ${varDef.scope +"_"+ varDef.name} dq 0")
            }
            output.appendLine("")
        }
    }


    /**
     * Generates the assembly code for the functions in the given AST nodes.
     * The output is appended to the provided StringBuilder.
     */
    fun generateFunctions(output: StringBuilder, nodes: List<ASTNode>) {
        output.appendLine("section \".text\" executable")

        val functionNodes = nodes.filterIsInstance<Function>()
        for (func in functionNodes) {
            generateFunction(func.name, func.body, output)
        }

        val mainFunction = functionNodes.find { it.name == "main" }
        if (mainFunction == null) {
            throw IllegalArgumentException("DefErr: 'main' was not defined!")
        }
    }



    /**
     * Generates the extern declarations for the functions in the given AST nodes.
     * The output is appended to the provided StringBuilder.
     */
    fun generateExternSection(output: StringBuilder, nodes: List<ASTNode>) {
        for (node in nodes) {
            if (node is Extern) {
                for (function in node.functionList) {
                    if (externalList.contains(function)) {
                        throw IllegalArgumentException("DefErr: 'external $function' already got defined!")
                    }
                    externalList.add(function)
                }
            }
        }
        if (externalList.isNotEmpty()) {
            for (function in externalList) {
                output.appendLine("extrn $function")
            }
            output.appendLine("")
        }
    }


    /**
     * Generates the assembly code for a single function.
     * The function name, body, and output StringBuilder are provided.
     */
    fun generateFunction(funcName: String, funcBody: List<ASTNode>,output: StringBuilder) {
        output.appendLine()
        output.appendLine("; --- $funcName ---")
        output.appendLine("public $funcName")
        output.appendLine("$funcName:")
        var hasExplicitReturn = false
        for (bodyNode in funcBody) {
            when (bodyNode) {

                /**
                 * Generates assembly code for an extern function call.
                 * If the function is in the external list, it uses `mov rdi, value`
                 * before calling the function.
                 */
                is Exit -> {
                    output.appendLine("    mov rax, 60")
                    if (!bodyNode.isNumber) {
                        output.appendLine("    mov rdi, [${bodyNode.scope +"_"+ bodyNode.value}]")

                    } else {
                        output.appendLine("    mov rdi, ${bodyNode.value}")

                    }
                    output.appendLine("    syscall")
                }


                /**
                 * Generates assembly code for an exit function call.
                 * If the value is a number, it uses `mov rdi, value`.
                 * Otherwise, it assumes the value is a variable and uses `mov rdi, [value]`.
                 */
                is FunctionCall -> {
                    if (externalList.contains(bodyNode.name)) {
                        if(!bodyNode.isNumber){
                            println(bodyNode)
                            output.appendLine("    mov rdi, [${bodyNode.scope +"_"+ bodyNode.value}]")
                            output.appendLine("    call ${bodyNode.name}")
                        }else{
                            output.appendLine("    mov rdi, ${bodyNode.value}")
                            output.appendLine("    call ${bodyNode.name}")
                        }
                    } else {
                        output.appendLine("    call ${bodyNode.name}")
                    }
                }


                /**
                 * Generates assembly code for a return statement.
                 * If the value is a number, it uses `mov eax, value`.
                 * Otherwise, it assumes the value is a variable and uses `mov eax, [value]`.
                 */
                is Return -> {
                    println(bodyNode)
                    hasExplicitReturn = true
                    if(!bodyNode.isNumber){
                        output.appendLine("    mov rax, [${bodyNode.scope +"_"+  bodyNode.value}]    ; return [${bodyNode.scope +"_"+ bodyNode.value}]")
                    } else {
                        output.appendLine("    mov rax, ${bodyNode.value}    ; return ${bodyNode.value}")
                    }
                    output.appendLine("    ret")
                }

                /**
                 * Generates assembly code for variable definitions.
                 * Variables are initialized in the function code.
                 */
                is VariableDef -> {
                    output.appendLine("    ; var ${bodyNode.name} = ${bodyNode.value}")
                    if (bodyNode.isNumber) {
                        output.appendLine("    mov qword [${funcName +"_"+ bodyNode.name}], ${bodyNode.value}")
                    } else {
                        output.appendLine("    mov rax, [${bodyNode.value}]")
                        output.appendLine("    mov qword [${bodyNode.name}], rax")
                    }
                }

                else -> {
                    output.appendLine("    ; Unbekannter Funktions-Body-Node: $bodyNode")
                }
            }
        }
        if (!hasExplicitReturn) {
            output.appendLine("    ret")
        }
    }
}