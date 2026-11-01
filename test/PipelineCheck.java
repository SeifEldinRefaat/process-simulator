import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PipelineCheck {

    static String output;

    static int run(String... program) throws Exception {
        Path file = Files.createTempFile("program", ".txt");
        Files.write(file, List.of(program));
        PrintStream console = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer));
        try {
            return Main.run(file.toString());
        } finally {
            System.setOut(console);
            output = buffer.toString();
            Files.delete(file);
        }
    }

    static void equal(int actual, int expected, String what) {
        if (actual != expected) {
            throw new AssertionError(what + ": expected " + expected + " but got " + actual);
        }
    }

    static void rejected(String what, String... program) throws Exception {
        try {
            run(program);
        } catch (Exception e) {
            return;
        }
        throw new AssertionError(what + " should have been rejected");
    }

    static int reg(int index) {
        return Main.readRegister(index);
    }

    static void checkPipelineTable() throws Exception {
        String[] program = new String[7];
        for (int i = 0; i < 7; ++i) {
            program[i] = "ADDI R" + (i + 1) + " R0 " + i;
        }
        equal(run(program), 19, "cycles for 7 instructions");

        String[] lines = output.split("\\R");
        int[] stageOfAge = {0, 1, 1, 2, 2, 3, 4};
        int position = 0;
        for (int cycle = 1; cycle <= 19; ++cycle) {
            equal(lines[position].equals("Cycle " + cycle) ? 1 : 0, 1, "header of cycle " + cycle);
            String[] expected = {"-", "-", "-", "-", "-"};
            for (int k = 1; k <= 7; ++k) {
                int age = cycle - (2 * k - 1);
                if (age >= 0 && age <= 6) {
                    expected[stageOfAge[age]] = "I" + k + " ";
                }
            }
            for (int s = 0; s < 5; ++s) {
                String actual = lines[position + 1 + s].split(": ", 2)[1];
                boolean matches = expected[s].equals("-") ? actual.equals("-") : actual.startsWith(expected[s]);
                equal(matches ? 1 : 0, 1, "cycle " + cycle + " stage " + Main.STAGE_NAMES[s] + " was: " + actual);
            }
            position += 6;
            while (lines[position].startsWith("  Update")) {
                ++position;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        checkPipelineTable();

        int cycles = run(
                "ADDI R1 R0 12",
                "ADDI R2 R1 -2",
                "",
                "ADD R3 R1 R2",
                "SUB R4 R2 R1",
                "MULI R5 R2 3",
                "ANDI R6 R1 10",
                "ORI R7 R1 3",
                "SLL R8 R1 2",
                "SRL R9 R4 28",
                "SLL R13 R1 32",
                "ADDI R0 R1 1",
                "ADDI R10 R0 1024",
                "SW R3 R10 1",
                "LW R11 R10 1",
                "ADD R12 R11 R11");
        equal(cycles, 7 + 14 * 2, "cycles for 15 instructions");
        int[] expected = {0, 12, 10, 22, -2, 30, 8, 15, 48, 15, 1024, 22, 44, 0};
        for (int i = 0; i < expected.length; ++i) {
            equal(reg(i), expected[i], "R" + i);
        }
        equal(Memory.toInt(Main.memory.ReadFromMemory(1025)), 22, "MEM[1025]");

        run("ADDI R1 R0 3",
                "ADDI R2 R0 0",
                "ADDI R2 R2 5",
                "ADDI R1 R1 -1",
                "BNE R1 R0 -3",
                "ADDI R3 R0 7");
        equal(reg(1), 0, "loop counter");
        equal(reg(2), 15, "loop total");
        equal(reg(3), 7, "instruction after the loop");

        run("J 2", "ADDI R1 R0 1", "ADDI R2 R0 1");
        equal(reg(1), 0, "flushed instruction");
        equal(reg(2), 1, "jump target");

        rejected("register R32", "ADD R32 R1 R2");
        rejected("too large an immediate", "ADDI R1 R2 131072");
        rejected("missing operand", "ADD R1 R2");
        rejected("unknown instruction", "NOP");
        rejected("load from the instruction segment", "LW R1 R0 5");
        rejected("store past the end of memory", "SW R1 R0 2048");
        rejected("branch to a negative address", "ADDI R1 R0 1", "BNE R1 R0 -5");

        System.out.println("All checks passed");
    }
}
