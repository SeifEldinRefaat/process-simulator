import java.io.*;

public class Memory {

    public static final int SIZE = 2048;
    public static final int DATA_START = 1024;

    private int[][] memory = new int[SIZE][32];
    private int instructionPosition = 0;

    public Memory(String path) throws Exception {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String st;
            int lineNumber = 0;
            while ((st = br.readLine()) != null) {
                ++lineNumber;
                if (st.isBlank()) {
                    continue;
                }
                try {
                    assemble(st.trim().split("\\s+"));
                } catch (Exception e) {
                    throw new Exception("Invalid instruction on line " + lineNumber + " \"" + st + "\": " + e.getMessage());
                }
            }
        }
    }

    private void assemble(String[] instruction) throws Exception {
        if (instructionPosition == DATA_START) {
            throw new Exception("the instruction segment holds at most " + DATA_START + " instructions");
        }
        Opcode e = Opcode.valueOf(instruction[0]);
        if (instruction.length != (e == Opcode.J ? 2 : 4)) {
            throw new Exception("wrong number of operands");
        }
        switch (e) {
            case ADD, SUB, SRL, SLL -> RFormat(e, instruction[1], instruction[2], instruction[3]);
            case MULI, ADDI, BNE, ANDI, ORI, LW, SW -> IFormat(e, instruction[1], instruction[2], instruction[3]);
            case J -> JFormat(e, instruction[1]);
        }
        ++instructionPosition;
    }

    public void RFormat(Opcode opcode, String R1, String R2, String R3) throws Exception {
        writeField(0, 4, opcode.ordinal());
        writeField(4, 5, parseRegister(R1));
        writeField(9, 5, parseRegister(R2));
        if (opcode == Opcode.SLL || opcode == Opcode.SRL) {
            writeField(19, 13, parseNumber(R3, 0, (1 << 13) - 1));
        } else {
            writeField(14, 5, parseRegister(R3));
        }
    }

    public void IFormat(Opcode opcode, String R1, String R2, String IMM) throws Exception {
        writeField(0, 4, opcode.ordinal());
        writeField(4, 5, parseRegister(R1));
        writeField(9, 5, parseRegister(R2));
        writeField(14, 18, parseNumber(IMM, -(1 << 17), (1 << 17) - 1));
    }

    public void JFormat(Opcode opcode, String address) throws Exception {
        writeField(0, 4, opcode.ordinal());
        writeField(4, 28, parseNumber(address, 0, (1 << 28) - 1));
    }

    private void writeField(int start, int width, int value) {
        for (int i = 0; i < width; ++i) {
            memory[instructionPosition][start + i] = (value >>> (width - 1 - i)) & 1;
        }
    }

    private int parseRegister(String name) throws Exception {
        if (!name.matches("R([0-9]|[12][0-9]|3[01])")) {
            throw new Exception("unknown register " + name);
        }
        return Integer.parseInt(name.substring(1));
    }

    private int parseNumber(String text, int min, int max) throws Exception {
        int num = Integer.parseInt(text);
        if (num < min || num > max) {
            throw new Exception(text + " is outside the range " + min + " to " + max);
        }
        return num;
    }

    public void WriteInMemory(int index, int[] value) {
        for (int i = 0; i < value.length; ++i) {
            memory[index][i] = value[i];
        }
    }

    public int[] ReadFromMemory(int index) {
        return memory[index];
    }

    public int[][] getMemory() {
        return memory;
    }

    public int getInstructionCount() {
        return instructionPosition;
    }

    public static int toInt(int[] bits) {
        int value = 0;
        for (int bit : bits) {
            value = (value << 1) | bit;
        }
        return value;
    }

    public static int[] toBits(int value) {
        int[] bits = new int[32];
        for (int i = 0; i < 32; ++i) {
            bits[i] = (value >>> (31 - i)) & 1;
        }
        return bits;
    }
}
