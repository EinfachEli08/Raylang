class Codegen(private val nodes:List<ASTNode>) {

    var externalList: MutableList<String> = mutableListOf()

    /**
     * Generates the assembly code for the given AST nodes.
     * The output is a StringBuilder containing the assembly code.
     */
    fun generateProgram(): StringBuilder {
        val output = StringBuilder()
        output.appendLine("format ELF64")
        output.appendLine("")

        println(nodes)

        generateFunctions(output, nodes)
        generateExternSection(output, nodes)

        output.appendLine("section \".data\"")
        //generateDataSection(output, nodes) Removed, as it is not used in the current implementation.

        return output
    }


    /**
     * Loads an argument into a specified register.
     * Handles different types of arguments such as dereferenced variables, references, literals, etc.
     * @param arg The argument to load.
     * @param reg The register to load the argument into.
     * @param output The StringBuilder to append the assembly code to.
     * @param varOffsetMap The map of variable names to their stack offsets.
     */
    fun loadArgToReg(arg: Arg, reg: String, output: StringBuilder, varOffsetMap: Map<String, Int> = emptyMap()) {
        when(arg) {
            is Arg.Deref -> {
                output.appendLine("    mov $reg, [rbp-${arg.index * 8}]")
                output.appendLine("    mov $reg, [${reg}]")
            }
            is Arg.RefAutoVar -> {
                val offset = (arg.index + 1) * 8
                output.appendLine("    mov $reg, [rbp-${offset}]")
            }
            is Arg.RefExternal -> output.appendLine("    lea $reg, [_${arg.name}]")
            is Arg.External -> output.appendLine("    mov $reg, [_${arg.name}]")
            is Arg.AutoVar -> {
                val offset = (arg.index + 1) * 8
                output.appendLine("    mov $reg, [rbp-${offset}]")
            }
            is Arg.Literal -> output.appendLine("    mov $reg, ${arg.value}")
            is Arg.DataOffset -> output.appendLine("    mov $reg, dat+${arg.offset}")
            is Arg.Bogus -> {
            }
        }
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
        var varNodes = nodes.filterIsInstance<VariableDef>().toMutableList()
        for (node in nodes) {
            if (node is Function) {
                val funcName = node.name
                for (stmt in node.body) {
                    if (stmt is VariableDef) {
                        varNodes.add(VariableDef(funcName, stmt.name, stmt.arg))
                    }
                }
            }
        }
        if (varNodes.isNotEmpty()) {
            for (varDef in varNodes) {
                // this is fine
                val rawData = varDef.arg.toString().toByteArray()

                output.append("dat: db ")
                for (i in rawData.indices) {
                    if (i > 0) {
                        output.append(",")
                    }
                    output.append(String.format("0x%02X", rawData[i]))
                }
                output.appendLine()
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

            var varDefCount = 0
            for (operation in func.body) {
                if (operation is FunctionCall) {
                    varDefCount++
                }
            }

            val variableCount = func.body.filterIsInstance<VariableDef>().size +
                    func.body.filterIsInstance<MultiVariableDef>().sumOf { it.names.size } +
                    varDefCount
            generateFunction(func.name, func.params, variableCount, func.body, output)
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
                output.appendLine("extrn '$function' as _${function}")
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
    fun generateFunction(funcName: String, funcParams:List<String>, varsCount:Int, funcBody: List<ASTNode>, output: StringBuilder) {
        val stackSize = alignBytes(varsCount * 8, 16)

        println("size: $stackSize for $funcName with $varsCount vars")

        output.appendLine()
        output.appendLine("public _$funcName as '$funcName'")
        output.appendLine("_$funcName:")
        output.appendLine("    push rbp")
        output.appendLine("    mov rbp, rsp")

        if(stackSize > 0 ){
            output.appendLine("    sub rsp, $stackSize")
        }

        val paramsCount = funcParams.size
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

        val varOffsetMap = buildVarOffsetMap(funcParams, funcBody)

        for (i in funcBody.indices) {
            output.appendLine(".op_${i}:")
            val operation = funcBody[i]
            when (operation) {

                /**
                 * Generates assembly code for an exit function call.
                 * If the value is a number, it uses `mov rdi, value`.
                 * Otherwise, it assumes the value is a variable and uses `mov rdi, [value]`.
                 */
                is Exit -> {
                    output.appendLine("    mov rax, 60")
                    loadArgToReg(operation.arg, "rdi", output, varOffsetMap)
                    output.appendLine("    syscall")
                }


                /**
                 * Generates assembly code for a function call.
                 * Handles multiple arguments by loading them into appropriate registers.
                 */
                is FunctionCall -> {
                    val argRegisters = arrayOf("rdi", "rsi", "rdx", "rcx", "r8", "r9")

                    // Load arguments into registers
                    for (j in operation.args.indices) {
                        if (j < argRegisters.size) {
                            loadArgToReg(operation.args[j], argRegisters[j], output, varOffsetMap)
                        } else {
                            // Handle stack arguments for more than 6 parameters
                            loadArgToReg(operation.args[j], "rax", output, varOffsetMap)
                            output.appendLine("    push rax")
                        }
                    }

                    output.appendLine("    mov al, 0")
                    output.appendLine("    call _${operation.name}")

                    // Clean up stack if we pushed arguments
                    val stackArgs = maxOf(0, operation.args.size - 6)
                    if (stackArgs > 0) {
                        output.appendLine("    add rsp, ${stackArgs * 8}")
                    }

                    val resultOffset = (funcParams.size + funcBody.filterIsInstance<VariableDef>().size +
                            funcBody.filterIsInstance<MultiVariableDef>().sumOf { it.names.size } + 1) * 8
                    output.appendLine("    mov [rbp-${resultOffset}], rax")
                }

                is FunctionCallAssign -> {
                    // Generate the function call first
                    val funcCall = operation.functionCall
                    val argRegisters = arrayOf("rdi", "rsi", "rdx", "rcx", "r8", "r9")

                    // Load arguments into registers
                    for (j in funcCall.args.indices) {
                        if (j < argRegisters.size) {
                            loadArgToReg(funcCall.args[j], argRegisters[j], output, varOffsetMap)
                        } else {
                            // Handle stack arguments for more than 6 parameters
                            loadArgToReg(funcCall.args[j], "rax", output, varOffsetMap)
                            output.appendLine("    push rax")
                        }
                    }

                    output.appendLine("    mov al, 0")
                    output.appendLine("    call _${funcCall.name}")

                    // Clean up stack if we pushed arguments
                    val stackArgs = maxOf(0, funcCall.args.size - 6)
                    if (stackArgs > 0) {
                        output.appendLine("    add rsp, ${stackArgs * 8}")
                    }

                    // Now assign the result (in rax) to the variable
                    val offset = varOffsetMap[operation.varName] ?: error("Variable ${operation.varName} not found in offset map!")
                    output.appendLine("    mov QWORD [rbp-${offset}], rax")
                }

                /**
                 * Generates assembly code for a return statement.
                 * If the value is a number, it uses `mov eax, value`.
                 * Otherwise, it assumes the value is a variable and uses `mov eax, [value]`.
                 */
                is Return -> {
                    println(operation)
                    if(operation.arg != Arg.Bogus) {
                        loadArgToReg(operation.arg, "rax", output, varOffsetMap)
                    }
                    output.appendLine("    mov rsp, rbp")
                    output.appendLine("    pop rbp")
                    output.appendLine("    ret")
                }

                /**
                 * Generates assembly code for variable definitions.
                 * Variables are initialized in the function code.
                 */
                is VariableDef -> {
                    loadArgToReg(operation.arg, "rax", output, varOffsetMap)
                    val offset = varOffsetMap[operation.name] ?: error("Variable ${operation.name} not found in offset map!")
                    output.appendLine("    mov QWORD [rbp-${offset}], rax")
                }

                /**
                 * Generates assembly code for multiple variable declarations.
                 * Initializes all variables to 0.
                 */
                is MultiVariableDef -> {
                    for (varName in operation.names) {
                        val offset = varOffsetMap[varName] ?: error("Variable $varName not found in offset map!")
                        output.appendLine("    mov QWORD [rbp-${offset}], 0")
                    }
                }

                /**
                 * Generates assembly code for variable assignments.
                 * Assigns a value to an existing variable.
                 */
                is VariableAssign -> {
                    loadArgToReg(operation.arg, "rax", output, varOffsetMap)
                    val offset = varOffsetMap[operation.name] ?: error("Variable ${operation.name} not found in offset map!")
                    output.appendLine("    mov QWORD [rbp-${offset}], rax")
                }

                else -> {
                    output.appendLine("    ; GEN-ERR: Unknown Func-Body-Node: $operation")
                }
            }
        }
        output.appendLine(".op_${funcBody.size}:")
        output.appendLine("    mov rax, 0")
        output.appendLine("    mov rsp, rbp")
        output.appendLine("    pop rbp")
        output.appendLine("    ret")
    }

    /**
     * Hilfsfunktion: Erzeugt eine Map von Variablennamen auf Stack-Offsets (rbp-Offset) für einen Funktionskontext.
     */
    fun buildVarOffsetMap(params: List<String>, body: List<ASTNode>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        var offset = 8

        // Add function parameters
        for (param in params) {
            map[param] = offset
            offset += 8
        }

        // Add local variables from VariableDef nodes
        for (node in body) {
            when (node) {
                is VariableDef -> {
                    map[node.name] = offset
                    offset += 8
                }
                is MultiVariableDef -> {
                    for (varName in node.names) {
                        map[varName] = offset
                        offset += 8
                    }
                }
                else -> {}
            }
        }

        return map
    }
}