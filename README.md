# CA: Processor Simulator

A Java simulator for **Package 1: Spicy Von Neumann Fillet with extra shifts** from the Computer System Architecture project description. It has a Von Neumann memory, 33 registers, and a five-stage pipeline that runs up to four instructions at once.

## Run

Requires JDK 16 or later. Run from the project root:

```sh
javac -d out/production/CA src/*.java
java -cp out/production/CA Main
```

This runs `assembly.txt`. To run another program, pass its path as an argument. You can also open the folder in IntelliJ IDEA and run `Main`.

To run the self-check:

```sh
javac -d out/check src/*.java test/*.java
java -cp out/check PipelineCheck
```

## Output

After each clock cycle, the simulator prints the cycle number, each stage's instruction and input values, and any register or memory changes. After the last cycle, it prints all registers and the full 2048 words of memory.

```
Cycle 13
  IF : I7 PC=6
  ID : I6 ANDI R13 R14 24 | read R14=0 imm=24
  EX : I5 BNE R11 R12 52 | R11=0 R12=0 imm=52 | not taken
  MEM: -
  WB : I4 ADDI R9 R10 82 | R9=82
  Update: R9: 0 -> 82
  Update: PC: 6 -> 7
```

## Project layout

| File | Purpose |
| --- | --- |
| `src/Main.java` | Pipeline stages, clock loop, and printing |
| `src/Memory.java` | 2048 words of memory and the assembler |
| `src/Registers.java` | `R0` to `R31` and `PC` |
| `src/Instructions.java` | One instruction moving through the pipeline |
| `src/Opcode.java` | Supported instructions, in opcode order |
| `test/PipelineCheck.java` | Self-check, including the pipeline table from the description |
| `assembly.txt` | Input program |
| `team.txt` | Team details required for submission |

## Instruction set

Write one instruction per line, separated by spaces. Blank lines are ignored.

| Opcode | Syntax | Format | Operation |
| --- | --- | --- | --- |
| 0 | `ADD R1 R2 R3` | R | `R1 = R2 + R3` |
| 1 | `SUB R1 R2 R3` | R | `R1 = R2 - R3` |
| 2 | `MULI R1 R2 IMM` | I | `R1 = R2 * IMM` |
| 3 | `ADDI R1 R2 IMM` | I | `R1 = R2 + IMM` |
| 4 | `BNE R1 R2 IMM` | I | `if (R1 != R2) PC = PC + 1 + IMM` |
| 5 | `ANDI R1 R2 IMM` | I | `R1 = R2 & IMM` |
| 6 | `ORI R1 R2 IMM` | I | `R1 = R2 \| IMM` |
| 7 | `J ADDRESS` | J | `PC = PC[31:28] \|\| ADDRESS` |
| 8 | `SLL R1 R2 SHAMT` | R | `R1 = R2 << SHAMT` |
| 9 | `SRL R1 R2 SHAMT` | R | `R1 = R2 >>> SHAMT` |
| 10 | `LW R1 R2 IMM` | I | `R1 = MEM[R2 + IMM]` |
| 11 | `SW R1 R2 IMM` | I | `MEM[R2 + IMM] = R1` |

Bits are numbered from 0, starting at the most significant bit.

| Format | Fields (bit ranges) |
| --- | --- |
| R | opcode (0-3), R1 (4-8), R2 (9-13), R3 (14-18), shamt (19-31) |
| I | opcode (0-3), R1 (4-8), R2 (9-13), immediate (14-31) |
| J | opcode (0-3), address (4-31) |

## Memory

Addresses 0 to 1023 hold instructions, and 1024 to 2047 hold data. `LW` and `SW` stop the program with an error if the address is outside the data segment.

## Pipeline

An instruction fetched in cycle `c` spends cycle `c` in IF, the next two in ID, the next two in EX, one in MEM, and one in WB. Instructions are fetched every other cycle, so IF and MEM never run in the same cycle. A program of `n` instructions with no taken branches takes `7 + (n - 1) * 2` cycles.

The description doesn't say how to handle hazards, and its cycle formula rules out stalls, so the simulator makes these choices:

- **Data hazards:** results are forwarded, so an instruction always sees the result of the one before it, including after `LW`. Cycle counts don't change.
- **Branches and jumps:** these resolve on the last EX cycle. The instruction in ID is flushed, and the target is fetched in that same cycle. A taken branch can change the total cycle count from the formula.
- **Jumps past the end of the program:** fetching stops, and the program ends once the pipeline is empty.
- **Immediates:** these are sign-extended, including for `ANDI` and `ORI`.
- **Writes to `R0`:** these are allowed but have no effect.

A program with an infinite loop runs until you stop it.
