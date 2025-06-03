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


    //TODO: Implement returning

    val asmFile = File(file.parentFile, file.nameWithoutExtension + ".asm")
    asmFile.writeText(output.toString())
}