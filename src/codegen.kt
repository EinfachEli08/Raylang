class Codegen(private val nodes:List<ASTNode>) {

    val externalList: MutableList<String> = mutableListOf()


    /**
     * Generates the assembly code for the given AST nodes.
     * The output is a StringBuilder containing the assembly code.
     */
    fun generateProgram(): StringBuilder {
        val output = StringBuilder()
        output.appendLine("format ELF64")

        generateExternals(output, nodes)
        generateFunctions(output, nodes)

        return output
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
    fun generateExternals(output: StringBuilder, nodes: List<ASTNode>) {
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
                    if (bodyNode.isNumber) {
                        output.appendLine("    mov rdi, ${bodyNode.value}")
                    } else {
                        output.appendLine("    mov rdi, [${bodyNode.value}]")
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
                        output.appendLine("    mov rdi, ${bodyNode.value}")
                        output.appendLine("    call ${bodyNode.name}")
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
                    hasExplicitReturn = true
                    output.appendLine("    mov eax, ${bodyNode.value}    ; return ${bodyNode.value}")
                    output.appendLine("    ret")
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