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

        println(nodes)

        generateExternSection(output, nodes)
        generateDataSection(output, nodes)
        generateFunctions(output, nodes)

        return output
    }


    fun loadNodeToReg(arg: ASTNode, reg:  String, output: StringBuilder) {
        when(arg) {
            /*
            arg.Deref(index) -> {
                sb.append(output, c!("    mov %s, [rbp-%zu]\n"), reg, index*8);
                sb.append(output, c!("    mov %s, [%s]\n"), reg, reg)
            }
            arg.RefAutoVar(index)  -> sb.append(output, c!("    lea %s, [rbp-%zu]\n"), reg, index*8),
            arg.RefExternal(name)  -> sb.append(output, c!("    lea %s, [_%s]\n"), reg, name),
            arg.External(name)     -> sb.append(output, c!("    mov %s, [_%s]\n"), reg, name),
            arg.AutoVar(index)     -> sb.append(output, c!("    mov %s, [rbp-%zu]\n"), reg, index*8),
            arg.Literal(value)     -> sb.append(output, c!("    mov %s, %ld\n"), reg, value),
            arg.DataOffset(offset) -> sb.append(output, c!("    mov %s, dat+%zu\n"), reg, offset),
             */
           // is Extern -> output.appendLine("    mov $reg, [${arg.}]")
            is Exit -> TODO()
            is Extern -> TODO()
            is Function -> TODO()
            is FunctionCall -> TODO()
            is Return -> TODO()
            is VariableDef -> TODO()
        };
    }


    /**
     * Aligns the given number of bytes to the specified alignment.
     * If the number of bytes is not already aligned, it rounds up to the next multiple of the alignment.
     * @param bytes The number of bytes to align.
     * @param alignment The alignment value (must be a power of 2).
     */
    fun alignBytes(bytes: Int, alignment: Int): Int {
        val rem = bytes % alignment
        return if (rem > 0) {
            bytes + alignment - rem
        } else {
            bytes
        }
    }

    /**
     * Sammelt alle Variablennamen aus VarDef-Nodes und deklariert sie im .bss-Abschnitt.
     * @param output The StringBuilder to append the assembly code to.
     * @param nodes The list of AST nodes to process.
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
                for (stmt in node.params) {
                    varNodes.add(VariableDef(funcName, stmt, "", false))
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
     * @param output The StringBuilder to append the assembly code to.
     * @param nodes The list of AST nodes to process.
     */
    fun generateFunctions(output: StringBuilder, nodes: List<ASTNode>) {
        output.appendLine("section \".text\" executable")

        val functionNodes = nodes.filterIsInstance<Function>()
        for (func in functionNodes) {
            println("func:" + func.name +"_"+ func.params)
            generateFunction(func.name, func.params,nodes.filterIsInstance<VariableDef>().size,func.body, output)
        }

        val mainFunction = functionNodes.find { it.name == "main" }
        if (mainFunction == null) {
            throw IllegalArgumentException("DefErr: 'main' was not defined!")
        }
    }



    /**
     * Generates the extern declarations for the functions in the given AST nodes.
     * The output is appended to the provided StringBuilder.
     * @param output The StringBuilder to append the assembly code to.
     * @param nodes The list of AST nodes to process.
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
     * @param funcName The name of the function.
     * @param funcParams The list of parameters for the function.
     * @param funcBody The body of the function as a list of AST nodes.
     * @param output The StringBuilder to append the assembly code to.
     */
    fun generateFunction(funcName: String, funcParams:List<String>, varsCount:Int,funcBody: List<ASTNode>,output: StringBuilder) {
        val stackSize = alignBytes(varsCount * 8, 16);
        output.appendLine()
        output.appendLine("; --- $funcName ---")
        output.appendLine("public $funcName")
        output.appendLine("$funcName:")
        output.appendLine("    push rbp")
        output.appendLine("    mov rbp, rsp")

        if(stackSize > 0 ){
            output.appendLine("    sub rsp, $stackSize")
        }

        val paramsCount = funcParams.size

       //& require(varsCount >= paramsCount)
        val registers: Array<String> = arrayOf("rdi", "rsi", "rdx", "rcx", "r8", "r9")

        var i = 0

        while (i < minOf(paramsCount, registers.size)) {
            output.appendLine("    mov QWORD [rbp-${(i + 1) * 8}], ${registers[i]}")
            i += 1
        }
        for (j in i until paramsCount) {
            output.appendLine("    mov QWORD rax, [rbp+${((j - i) + 2) * 8}]")
            output.appendLine("    mov QWORD [rbp-${(j + 1) * 8}], rax")
        }

        println(funcName +"_"+ funcParams)

        var hasExplicitReturn = false

        for (i in funcBody.indices) {
            output.appendLine(".op_${i}:")
            val operation = funcBody[i]
            when (operation) {

                /**
                 * Generates assembly code for an extern function call.
                 * If the function is in the external list, it uses `mov rdi, value`
                 * before calling the function.
                 */
                is Exit -> {
                    output.appendLine("    mov rax, 60")
                    if (!operation.isNumber) {
                        output.appendLine("    mov rdi, [${operation.scope +"_"+ operation.value}]")

                    } else {
                        output.appendLine("    mov rdi, ${operation.value}")

                    }
                    output.appendLine("    syscall")
                }


                /**
                 * Generates assembly code for an exit function call.
                 * If the value is a number, it uses `mov rdi, value`.
                 * Otherwise, it assumes the value is a variable and uses `mov rdi, [value]`.
                 */
                is FunctionCall -> {
                    if (externalList.contains(operation.name)) {
                        if(!operation.isNumber){
                            println(operation)
                            output.appendLine("    mov rdi, [${operation.scope +"_"+ operation.value}]")
                            output.appendLine("    call ${operation.name}")
                        }else{
                            output.appendLine("    mov rdi, ${operation.value}")
                            output.appendLine("    call ${operation.name}")
                        }
                    } else {
                        output.appendLine("    call ${operation.name}")
                    }
                }


                /**
                 * Generates assembly code for a return statement.
                 * If the value is a number, it uses `mov eax, value`.
                 * Otherwise, it assumes the value is a variable and uses `mov eax, [value]`.
                 */
                is Return -> {
                    println(operation)
                    hasExplicitReturn = true
                    if(!operation.isNumber){
                        output.appendLine("    mov rax, [${operation.scope +"_"+  operation.value}]    ; return [${operation.scope +"_"+ operation.value}]")
                    } else {
                        output.appendLine("    mov rax, ${operation.value}    ; return ${operation.value}")
                    }
                    output.appendLine("    pop rbp")
                    output.appendLine("    ret")
                }

                /**
                 * Generates assembly code for variable definitions.
                 * Variables are initialized in the function code.
                 */
                is VariableDef -> {
                    output.appendLine("    ; var ${operation.name} = ${operation.value}")
                    if (operation.isNumber) {
                        output.appendLine("    mov qword [${funcName +"_"+ operation.name}], ${operation.value}")
                    } else {
                        output.appendLine("    mov rax, [${operation.value}]")
                        output.appendLine("    mov qword [${operation.name}], rax")
                    }
                }

                else -> {
                    output.appendLine("    ; Unbekannter Funktions-Body-Node: $operation")
                }
            }
        }
        if (!hasExplicitReturn) {
            output.appendLine(".op_${funcBody.size}:")
            output.appendLine("    mov rax, 0")
            output.appendLine("    mov rsp, rbp")
            output.appendLine("    pop rbp")
            output.appendLine("    ret")
        }
    }
}