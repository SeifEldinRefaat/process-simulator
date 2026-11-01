public class Registers {

    public static final int PC = 32;

    private int[][] registers;

    public Registers(){
        this.registers = new int[33][32];
    }

    public void WriteInRegisters(int index , int[] value) {
        if(index != 0){
            registers[index] = value.clone();
        }
    }

    public int[] ReadFromRegister(int index){
        return registers[index];
    }
}
