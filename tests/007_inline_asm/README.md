# Test case: Inline assembly

In this test, an inline assembly function will be executed
If everything is compiled fine, the Program shoud exit with exit code 0

## Build instructions

1. Run `make` to build the test program.
2. The generated binary `main` can then be executed.

## Files

- `main.asm`: Assembly source code for the test.
- `makefile`: Build script for creating the test.

## Requirements
- `make` (for building the test)
- `fasm` (Flat Assembler)
- `gcc` (GNU Compiler Collection, for linking and temporary standard library)
