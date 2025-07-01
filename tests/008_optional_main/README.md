# Test case: Optional Main function

In this test, We are attempting to run code without defining a Main entry point
We are also testing the Order, if it matters where code is located without a central main

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
