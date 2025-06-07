import java.io.File

fun main(args: Array<String>){

    if (args.isEmpty()) {
        println("Bitte einen Dateipfad als Argument übergeben.")
        return
    }

    val filePath = args[0]
    val file = File(filePath)
    val source = file.readText()
    val lexer = Lexer()
    val tokens = lexer.tokenize(source)

    println("Tokens:  $tokens")

    val output = StringBuilder()
    output.appendLine("format ELF64")
    output.appendLine("section \".text\" executable")
    output.appendLine("public main")
    output.appendLine("EXTERNALPLACEHOLDER")


    val externalList: MutableList<String> = mutableListOf()
    val externalStringList = StringBuilder()

    val parser = Parser(tokens)
    val nodes = parser.parseAll()

    // Funktions-Nodes extrahieren
    val functionNodes = nodes.filterIsInstance<FunctionNode>()
    val mainFunction = functionNodes.find { it.name == "main" }
    if (mainFunction == null) {
        System.err.println("Fehler: Keine Funktion 'main' gefunden!")
        return
    }

    // Externals wie gehabt sammeln
    for (node in nodes) {
        if (node is ExternNode) {
            for (function in node.functionList) {
                if (externalList.contains(function)) {
                    throw IllegalArgumentException("DefErr: 'external $function' already got defined!")
                }
                externalList.add(function)
            }
        }
    }

    // Assembly für alle Funktionen generieren
    for (func in functionNodes) {
        output.appendLine()
        output.appendLine("; --- ${func.name} ---")
        output.appendLine("${func.name}:")
        println(func)
        var hasExplicitReturn = false
        for (bodyNode in func.body) {
            when (bodyNode) {
                is ExitNode -> {
                    output.appendLine("    mov rax, 60")
                    if (bodyNode.isNumber) {
                        output.appendLine("    mov rdi, ${bodyNode.value}")
                    } else {
                        output.appendLine("    mov rdi, [${bodyNode.value}]")
                    }
                    output.appendLine("    syscall")
                }
                is FunctionCallNode -> {
                    // Unterscheide zwischen externen und eigenen Funktionen
                    if (externalList.contains(bodyNode.name)) {
                        output.appendLine("    mov rdi, ${bodyNode.value}")
                        output.appendLine("    call ${bodyNode.name}")
                    } else {
                        output.appendLine("    call ${bodyNode.name}")
                    }
                }
                is ReturnNode -> {
                    hasExplicitReturn = true
                    output.appendLine("    mov eax, ${bodyNode.value}    ; return ${bodyNode.value}")
                    output.appendLine("    ret")
                }
                else -> {
                    output.appendLine("    ; Unbekannter Funktions-Body-Node: $bodyNode")
                }
            }
        }
        // Falls kein explizites return vorhanden, ret am Ende einfügen
        if (!hasExplicitReturn) {
            output.appendLine("    ret")
        }
    }

    // Main-Label als Entry-Point deklarieren
    output.replace(output.indexOf("main:"), output.indexOf("main:") + "main:".length, "main:")

    // Externals einfügen wie gehabt
    if (externalList.isNotEmpty()) {
        for (function in externalList) {
            externalStringList.appendLine("extrn ${function}")
        }
        output.insert(output.indexOf("EXTERNALPLACEHOLDER"), externalStringList.toString())
        output.replace(output.indexOf("EXTERNALPLACEHOLDER"), output.indexOf("EXTERNALPLACEHOLDER") + "EXTERNALPLACEHOLDER".length, "")
    }

    println()
    println("Ray to assembly compiled code:")
    println()
    println(output.toString())
    println("File saved to ${file.parentFile?.absolutePath ?: "."}, ${file.nameWithoutExtension}.asm")

    val asmFile = File(file.parentFile, file.nameWithoutExtension + ".asm")
    asmFile.writeText(output.toString())
}
