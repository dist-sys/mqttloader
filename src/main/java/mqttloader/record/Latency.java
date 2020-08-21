package mqttloader.record;

public class Latency {
    private final int slot;
    private final int latency;

    public Latency(int slot, int latency) {
        this.slot = slot;
        this.latency = latency;
    }

    public int getSlot() {
        return slot;
    }

    public int getLatency() {
        return latency;
    }
}
