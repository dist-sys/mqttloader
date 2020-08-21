package mqttloader;

public class Constants {
    public static final String SUB_CLIENT_ID_PREFIX = "mqttloaderclient-sub";
    public static final String PUB_CLIENT_ID_PREFIX = "mqttloaderclient-pub";

    public enum Opt {
        BROKER("b", "broker", true, "Broker URL. E.g., tcp://127.0.0.1:1883", null, true),
        VERSION("v", "version", true, "MQTT version (\"3\" for 3.1.1 or \"5\" for 5.0).", "5"),
        NUM_PUB("p", "npub", true, "Number of publishers.", "10"),
        NUM_SUB("s", "nsub", true, "Number of subscribers.", "0"),
        PUB_QOS("pq", "pubqos", true, "QoS level of publishers (0/1/2).", "0"),
        SUB_QOS("sq", "subqos", true, "QoS level of subscribers (0/1/2).", "0"),
        SH_SUB("ss", "shsub", false, "Enable shared subscription.", null),
        RETAIN("r", "retain", false, "Enable retain.", null),
        TOPIC("t", "topic", true, "Topic name to be used.", "mqttloader-test-topic"),
        PAYLOAD("d", "payload", true, "Data (payload) size in bytes.", "1024"),
        NUM_MSG("m", "nmsg", true, "Number of messages sent by each publisher.", "100"),
        RAMP_UP("ru", "rampup", true, "Ramp-up time in seconds.", "0"),
        RAMP_DOWN("rd", "rampdown", true, "Ramp-down time in seconds.", "0"),
        INTERVAL("i", "interval", true, "Publish interval in milliseconds.", "0"),
        SUB_TIMEOUT("st", "subtimeout", true, "Subscribers' timeout in seconds.", "5"),
        EXEC_TIME("et", "exectime", true, "Execution time in seconds.", "60"),
        LOG_LEVEL("l", "log", true, "Log level (SEVERE/WARNING/INFO/ALL).", "WARNING"),
        NTP("n", "ntp", true, "NTP server. E.g., ntp.nict.jp", null),
        TH_FILE("tf", "thfile", true, "File name for throughput data.", null),
        LT_FILE("lf", "ltfile", true, "File name for latency data.", null),
        HELP("h", "help", false, "Display help.", null);

        private String name;
        private String longOpt;
        private boolean hasArg;
        private boolean required;
        private String description;
        private String defaultValue;

        private Opt(String name, String longOpt, boolean hasArg, String description, String defaultValue) {
            this(name, longOpt, hasArg, description, defaultValue, false);
        }

        private Opt(String name, String longOpt, boolean hasArg, String description, String defaultValue, boolean required) {
            this.name = name;
            this.longOpt = longOpt;
            this.hasArg = hasArg;
            this.description = description;
            this.defaultValue = defaultValue;
            this.required = required;
        }

        public String getName() {
            return name;
        }

        public String getLongOpt() {
            return longOpt;
        }

        public boolean hasArg() {
            return hasArg;
        }

        public boolean isRequired() {
            return required;
        }

        public String getDescription() {
            return description;
        }

        public String getDefaultValue() {
            return defaultValue;
        }
    }
}
