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
    output.appendLine("main:")

    val externalList: MutableList<String> = mutableListOf()
    val externalStringList = StringBuilder()

    val parser = Parser(tokens)
    val nodes = parser.parseAll()
    for (node in nodes) {
        when (node) {
            is ExitNode -> {
                output.appendLine(      "    mov rax, 60")
                if (node.isNumber) {
                    output.appendLine(  "    mov rdi, ${node.value}")
                } else {
                    output.appendLine(  "    mov rdi, [${node.value}]")
                }
                 output.appendLine(      "    syscall")
            }

            is FunctionCallNode -> {
                output.appendLine("    mov rdi, ${node.value}")
                output.appendLine("    call ${node.name}")
            }

            is ReturnNode -> {
                output.appendLine(      "    mov rax, ${node.value}")
                output.appendLine("    ret")
            }

            is ExternNode -> {
               for (function in node.functionList) {
                   if (externalList.contains(function)) {
                       throw IllegalArgumentException("DefErr: 'external $function' already got defined!")
                   }
                   externalList.add(function)

               }
            }

            else -> {
                println("Unknown ASTNode-Type: $node")
            }
        }
    }

    // if available, add extern functions to the output
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

