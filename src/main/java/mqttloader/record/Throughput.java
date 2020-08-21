package mqttloader.record;

public class Throughput {
    private final int slot;
    private int count;

    public Throughput(int slot, int count) {
        this.slot = slot;
        this.count = count;
    }

    public int getSlot() {
        return slot;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
