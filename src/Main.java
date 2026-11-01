import java.util.ArrayList;
import java.util.List;

public class Main {

    static final String[] STAGE_NAMES = {"IF ", "ID ", "EX ", "MEM", "WB "};

    static Memory memory;
    static Registers registers;
    static List<Instructions> inFlight;
    static int cycle;
    static int fetched;
    static String[] stages;
    static List<String> updates;

    public static void IF(){
        int pc = readRegister(Registers.PC);
        Instructions in = new Instructions();
        in.number = ++fetched;
        in.fetchCycle = cycle;
        in.pc = pc;
        in.word = Memory.toInt(memory.ReadFromMemory(pc));
        inFlight.add(in);
        writeRegister(Registers.PC, pc + 1);
        stages[0] = "I" + in.number + " PC=" + pc;
    }

    public static void ID(Instructions in, boolean lastCycle){
        if(!lastCycle){
            int w = in.word;
            in.opcode = Opcode.values()[w >>> 28];
            in.r1 = (w >>> 23) & 31;
            in.r2 = (w >>> 18) & 31;
            in.r3 = (w >>> 13) & 31;
            in.shamt = w & 0x1FFF;
            in.imm = (w << 14) >> 14;
            in.address = w & 0x0FFFFFFF;
            stages[1] = "I" + in.number + " " + text(in) + " | word=" + binary(w);
            return;
        }
        readOperands(in);
        stages[1] = "I" + in.number + " " + text(in) + " | read " + inputs(in);
    }

    public static void EX(Instructions in, boolean lastCycle){
        if(!lastCycle){
            stages[2] = "I" + in.number + " " + text(in) + " | " + inputs(in);
            return;
        }

        readOperands(in);
        String outcome = "";
        switch(in.opcode){
            case ADD -> in.result = in.valR2 + in.valR3;
            case SUB -> in.result = in.valR2 - in.valR3;
            case MULI -> in.result = in.valR2 * in.imm;
            case ADDI, LW, SW -> in.result = in.valR2 + in.imm;
            case ANDI -> in.result = in.valR2 & in.imm;
            case ORI -> in.result = in.valR2 | in.imm;
            case SLL -> in.result = in.shamt >= 32 ? 0 : in.valR2 << in.shamt;
            case SRL -> in.result = in.shamt >= 32 ? 0 : in.valR2 >>> in.shamt;
            case BNE -> {
                boolean taken = in.valR1 != in.valR2;
                outcome = taken ? "taken" : "not taken";
                if(taken){
                    jump(in, in.pc + 1 + in.imm);
                }
            }
            case J -> {
                outcome = "taken";
                jump(in, (in.pc & 0xF0000000) | in.address);
            }
        }
        if(outcome.isEmpty()){
            outcome = "result=" + in.result;
        }
        stages[2] = "I" + in.number + " " + text(in) + " | " + inputs(in) + " | " + outcome;
    }

    public static void MEM(Instructions in) throws Exception {
        String action = "no memory access";
        if(in.opcode == Opcode.LW || in.opcode == Opcode.SW){
            int address = in.result;
            if(address < Memory.DATA_START || address >= Memory.SIZE){
                throw new Exception("I" + in.number + " " + text(in) + ": address " + address
                        + " is outside the data segment (" + Memory.DATA_START + " to " + (Memory.SIZE - 1) + ")");
            }
            if(in.opcode == Opcode.LW){
                in.result = Memory.toInt(memory.ReadFromMemory(address));
                action = "address=" + address + " | loaded " + in.result;
            }else{
                int old = Memory.toInt(memory.ReadFromMemory(address));
                memory.WriteInMemory(address, Memory.toBits(in.valR1));
                action = "address=" + address + " | stored " + in.valR1;
                updates.add("MEM[" + address + "]: " + old + " -> " + in.valR1);
            }
        }
        stages[3] = "I" + in.number + " " + text(in) + " | " + action;
    }

    public static void WB(Instructions in){
        String action = "no register write";
        if(in.opcode != Opcode.BNE && in.opcode != Opcode.J && in.opcode != Opcode.SW){
            writeRegister(in.r1, in.result);
            action = "R" + in.r1 + "=" + in.result;
        }
        stages[4] = "I" + in.number + " " + text(in) + " | " + action;
    }

    static void readOperands(Instructions in){
        in.valR1 = readRegister(in.r1);
        in.valR2 = readRegister(in.r2);
        in.valR3 = readRegister(in.r3);
    }

    static void jump(Instructions in, int target){
        if(target < 0){
            throw new IllegalStateException("I" + in.number + " " + text(in) + ": jump target " + target + " is negative");
        }
        writeRegister(Registers.PC, target);
        List<Instructions> younger = inFlight.subList(inFlight.indexOf(in) + 1, inFlight.size());
        for(Instructions flushed : younger){
            updates.add("Flushed I" + flushed.number);
        }
        younger.clear();
    }

    static int readRegister(int index){
        return Memory.toInt(registers.ReadFromRegister(index));
    }

    static void writeRegister(int index, int value){
        int old = readRegister(index);
        registers.WriteInRegisters(index, Memory.toBits(value));
        int now = readRegister(index);
        if(now != old){
            updates.add(registerName(index) + ": " + old + " -> " + now);
        }
    }

    static String registerName(int index){
        return index == Registers.PC ? "PC" : "R" + index;
    }

    static String text(Instructions in){
        return switch(in.opcode){
            case ADD, SUB -> in.opcode + " R" + in.r1 + " R" + in.r2 + " R" + in.r3;
            case SLL, SRL -> in.opcode + " R" + in.r1 + " R" + in.r2 + " " + in.shamt;
            case J -> in.opcode + " " + in.address;
            default -> in.opcode + " R" + in.r1 + " R" + in.r2 + " " + in.imm;
        };
    }

    static String inputs(Instructions in){
        return switch(in.opcode){
            case ADD, SUB -> "R" + in.r2 + "=" + in.valR2 + " R" + in.r3 + "=" + in.valR3;
            case SLL, SRL -> "R" + in.r2 + "=" + in.valR2 + " shamt=" + in.shamt;
            case BNE, SW -> "R" + in.r1 + "=" + in.valR1 + " R" + in.r2 + "=" + in.valR2 + " imm=" + in.imm;
            case J -> "PC=" + in.pc + " address=" + in.address;
            default -> "R" + in.r2 + "=" + in.valR2 + " imm=" + in.imm;
        };
    }

    static String binary(int value){
        return String.format("%32s", Integer.toBinaryString(value)).replace(' ', '0');
    }

    static void printCycle(){
        System.out.println("Cycle " + cycle);
        for(int i = 0; i < stages.length; ++i){
            System.out.println("  " + STAGE_NAMES[i] + ": " + stages[i]);
        }
        for(String update : updates){
            System.out.println("  Update: " + update);
        }
    }

    static void printFinalState(){
        System.out.println();
        System.out.println("Finished after " + cycle + " clock cycles");
        System.out.println();
        System.out.println("Registers:");
        for(int i = 0; i <= Registers.PC; ++i){
            System.out.println("  " + registerName(i) + " = " + readRegister(i));
        }
        System.out.println();
        System.out.println("Memory (address: bits = decimal):");
        for(int i = 0; i < Memory.SIZE; ++i){
            if(i == 0){
                System.out.println("Instruction segment");
            }else if(i == Memory.DATA_START){
                System.out.println("Data segment");
            }
            int word = Memory.toInt(memory.ReadFromMemory(i));
            System.out.println("  " + i + ": " + binary(word) + " = " + word);
        }
    }

    static int run(String path) throws Exception {
        memory = new Memory(path);
        registers = new Registers();
        inFlight = new ArrayList<>();
        cycle = 0;
        fetched = 0;

        while(!inFlight.isEmpty() || readRegister(Registers.PC) < memory.getInstructionCount()){
            ++cycle;
            stages = new String[]{"-", "-", "-", "-", "-"};
            updates = new ArrayList<>();

            for(int i = 0; i < inFlight.size(); ++i){
                Instructions in = inFlight.get(i);
                switch(cycle - in.fetchCycle){
                    case 1 -> ID(in, false);
                    case 2 -> ID(in, true);
                    case 3 -> EX(in, false);
                    case 4 -> EX(in, true);
                    case 5 -> MEM(in);
                    case 6 -> WB(in);
                }
            }
            inFlight.removeIf(in -> cycle - in.fetchCycle == 6);

            if(cycle % 2 == 1 && readRegister(Registers.PC) < memory.getInstructionCount()){
                IF();
            }
            printCycle();
        }
        printFinalState();
        return cycle;
    }

    public static void main(String[] args) throws Exception {
        run(args.length > 0 ? args[0] : "assembly.txt");
    }
}
