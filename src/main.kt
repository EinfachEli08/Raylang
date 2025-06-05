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
    output.appendLine("main:")

    val parser = Parser(tokens)
    val nodes = parser.parseAll()
    for (node in nodes) {
        when (node) {
            is ReturnNode -> {
                output.appendLine(      "    mov rax, 60")
                if (node.isNumber) {
                    output.appendLine(  "    mov rdi, ${node.value}")
                } else {
                    output.appendLine(  "    mov rdi, [${node.value}]")
                }
                val comment = node.inlineComment?.let { " ; what happens here?".replace("what happens here?", it) } ?: ""
                output.appendLine(      "    syscall        ; return" + comment)
            }
            is CommentNode -> {
                if (node.isMultiLine) {
                    node.lines?.forEach {
                        output.appendLine("    ; ${it}")
                    }
                    output.appendLine("    ; (multi-line comment)")
                } else {
                    output.appendLine("    ; ${node.text} (single-line comment)")
                }
            }
            else -> {
                println("Unknown ASTNode-Type: $node")
            }
        }
    }

    println()
    println("Ray to assembly compiled code:")
    println()
    println(output.toString())
    println("File saved to ${file.parentFile?.absolutePath ?: "."}, ${file.nameWithoutExtension}.asm")

    val asmFile = File(file.parentFile, file.nameWithoutExtension + ".asm")
    asmFile.writeText(output.toString())
}

