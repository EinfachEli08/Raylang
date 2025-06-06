test.o: main.asm
	fasm main.asm

test: test.o
	gcc -no-pie -o test test.o
