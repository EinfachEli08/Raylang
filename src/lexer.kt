enum class TokenType {
    IDENTIFIER,
    KEYWORD,
    NUMBER,
    STRING,
    OPERATOR,
    SEPARATOR,
    COMMENT,
    OPEN_COMMENT,
    CLOSED_COMMENT,
    TEXT,
    ENDL,
    EOF
}

data class Token(
    val type: TokenType,
    val value: String,
    val position: Int,
    val line: Int,
    val column: Int,
    val isMultiLine: Boolean? = null
)

class Lexer {
    private val keywords = setOf("func", "var", "return", "extern","exit", "import")
    private val operators = setOf("+", "-", "*", "/", "=", "==", "=>")
    private val separators = setOf("(", ")", "{", "}", ";", ",", ":", "?")
    private val commentStart = "//"
    private val multiLineCommentStart = "/*"
    private val multiLineRayDocCommentStart = "/**"
    private val multiLineCommentEnd = "*/"


    /**
     * Tokenizes the input string into a list of tokens.
     * Handles comments, strings, identifiers, numbers, operators, and separators.
     *
     * @param input The source code to tokenize.
     * @return A list of tokens extracted from the input.
     */
    fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        var line = 1
        var column = 1
        val length = input.length

        while (i < length) {
            val c = input[i]
            val start = i
            val startLine = line
            val startColumn = column

            when {
                // Single line comment
                input.startsWith(commentStart, i) -> {
                    tokens.add(Token(TokenType.COMMENT, commentStart, start, startLine, startColumn, isMultiLine = false))
                    i += commentStart.length
                    column += commentStart.length
                    val textStart = i
                    val end = input.indexOf('\n', i).takeIf { it != -1 } ?: length
                    val commentText = input.substring(textStart, end)
                    if (commentText.isNotEmpty()) {
                        tokens.add(Token(TokenType.TEXT, commentText, textStart, startLine, column, isMultiLine = false))
                    }
                    i = end
                    tokens.add(Token(TokenType.ENDL, "ENDL", i, line, column + commentText.length))
                    line++
                    column = 1
                    if (i < length) i++ // consume '\n'
                }

                // RayDoc-Comment
                input.startsWith(multiLineRayDocCommentStart, i) -> {
                    tokens.add(Token(TokenType.OPEN_COMMENT, multiLineRayDocCommentStart, start, startLine, startColumn, isMultiLine = true))
                    i += multiLineRayDocCommentStart.length
                    column += multiLineRayDocCommentStart.length
                    val end = input.indexOf(multiLineCommentEnd, i).takeIf { it != -1 } ?: length
                    val commentBody = input.substring(i, end)
                    val lines = commentBody.split('\n')
                    for ((idx, lineText) in lines.withIndex()) {
                        if (lineText.isNotEmpty()) {
                            tokens.add(Token(TokenType.TEXT, lineText, i, line, column, isMultiLine = true))
                        }
                        if (idx < lines.lastIndex) {
                            tokens.add(Token(TokenType.ENDL, "ENDL", i, line, column + lineText.length))
                            line++
                            column = 1
                        } else {
                            column += lineText.length
                        }
                        i += lineText.length
                        if (idx < lines.lastIndex) i++ // consume '\n'
                    }
                    i = end + multiLineCommentEnd.length
                    column += multiLineCommentEnd.length
                }

                // Multi-line comment
                input.startsWith(multiLineCommentStart, i) -> {
                    tokens.add(Token(TokenType.OPEN_COMMENT, multiLineCommentStart, start, startLine, startColumn, isMultiLine = true))
                    i += multiLineCommentStart.length
                    column += multiLineCommentStart.length
                    val end = input.indexOf(multiLineCommentEnd, i).takeIf { it != -1 } ?: length
                    val commentBody = input.substring(i, end)
                    val lines = commentBody.split('\n')
                    for ((idx, lineText) in lines.withIndex()) {
                        if (lineText.isNotEmpty()) {
                            tokens.add(Token(TokenType.TEXT, lineText, i, line, column, isMultiLine = true))
                        }
                        if (idx < lines.lastIndex) {
                            tokens.add(Token(TokenType.ENDL, "ENDL", i, line, column + lineText.length))
                            line++
                            column = 1
                        } else {
                            column += lineText.length
                        }
                        i += lineText.length
                        if (idx < lines.lastIndex) i++ // consume '\n'
                    }
                    i = end + multiLineCommentEnd.length
                    column += multiLineCommentEnd.length
                }

                c.isWhitespace() && c != '\n' -> {
                    i++
                    column++
                }

                c == '\n' -> {
                    tokens.add(Token(TokenType.ENDL, "ENDL", i, line, column))
                    i++
                    line++
                    column = 1
                }

                c == '"' -> {
                    i++
                    var literal = ""
                    while (i < length && input[i] != '"') {
                        literal += input[i]
                        if (input[i] == '\n') {
                            line++
                            column = 0
                        }
                        i++
                        column++
                    }
                    i++
                    column++
                    tokens.add(Token(TokenType.STRING, literal, start, startLine, startColumn))
                }

                c.isLetter() -> {
                    while (i < length && (input[i].isLetterOrDigit() || input[i] == '_')) {
                        i++
                        column++
                    }
                    val word = input.substring(start, i)
                    val type = if (word in keywords) TokenType.KEYWORD else TokenType.IDENTIFIER
                    tokens.add(Token(type, word, start, startLine, startColumn))
                }

                c.isDigit() -> {
                    while (i < length && input[i].isDigit()) {
                        i++
                        column++
                    }
                    tokens.add(Token(TokenType.NUMBER, input.substring(start, i), start, startLine, startColumn))
                }

                operators.any { input.startsWith(it, i) } -> {
                    val op = operators.first { input.startsWith(it, i) }
                    tokens.add(Token(TokenType.OPERATOR, op, start, startLine, startColumn))
                    i += op.length
                    column += op.length
                }

                separators.contains(c.toString()) -> {
                    tokens.add(Token(TokenType.SEPARATOR, c.toString(), start, startLine, startColumn))
                    i++
                    column++
                }

                else -> {
                    throw IllegalArgumentException("Unexpected character '$c' at line $line, column $column")
                }
            }
        }

        tokens.add(Token(TokenType.EOF, "EOF", i, line, column))
        return tokens
    }
}
