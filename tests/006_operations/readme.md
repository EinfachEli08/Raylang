# Test case: Functions calling functions

In this test, variables are being defined and modified.
Variables can modify variables and they can be used in functions as parameters or returns.
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
