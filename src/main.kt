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

    val parser = Parser(tokens)
    val nodes = parser.parseAll()

    val gen = Codegen(nodes)
    val output = gen.generateProgram()

    println("File saved to ${file.parentFile?.absolutePath ?: "."}, ${file.nameWithoutExtension}.asm")

    val asmFile = File(file.parentFile, file.nameWithoutExtension + ".asm")
    asmFile.writeText(output.toString())
}
