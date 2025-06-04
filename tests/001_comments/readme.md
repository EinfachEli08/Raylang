# Test case: Return value

In this test, it is checked whether a return value can be processed correctly.

**Note:**  
This target may only be executed before the main entry point has been implemented.  
Afterwards, this test is non-functional.

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