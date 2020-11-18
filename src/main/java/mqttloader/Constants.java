/*
 * Copyright 2020 Distributed Systems Group
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mqttloader;

import java.text.SimpleDateFormat;

public class Constants {
    public static final String VERSION = "0.7.3";
    public static final String BROKER_URL_PREFIX_TCP = "tcp://";
    public static final String BROKER_URL_PREFIX_TLS = "ssl://";
    public static final String BROKER_URL_PORT_TCP = ":1883";
    public static final String BROKER_URL_PORT_TLS = ":8883";
    public static final String FILE_NAME_PREFIX = "mqttloader_";
    private static final String HOST_ID = Util.genRandomChars(4);
    public static final String SUB_CLIENT_ID_PREFIX = "ml-"+HOST_ID+"-s-";
    public static final String PUB_CLIENT_ID_PREFIX = "ml-"+HOST_ID+"-p-";
    public static final Record STOP_SIGNAL = new Record();
    public static final int MILLISECOND_IN_NANO = 1000000;
    public static final int SECOND_IN_NANO = 1000000000;
    public static final int SECOND_IN_MILLI = 1000;
    public static final SimpleDateFormat DATE_FORMAT_FOR_LOG = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z");
    public static final SimpleDateFormat DATE_FORMAT_FOR_FILENAME = new SimpleDateFormat("yyyyMMdd-HHmmss");
    public static final String TLS_TRUSTSTORE_FILENAME = "truststore.jks";
    public static final String TLS_KEYSTORE_FILENAME = "keystore.jks";

    public enum Opt {
        BROKER("b", "broker", true, "Broker URL. E.g., tcp://127.0.0.1:1883", null, true),
        MQTT_VERSION("v", "version", true, "MQTT version (\"3\" for 3.1.1 or \"5\" for 5.0).", "5"),
        NUM_PUB("p", "npub", true, "Number of publishers.", "1"),
        NUM_SUB("s", "nsub", true, "Number of subscribers.", "1"),
        PUB_QOS("pq", "pubqos", true, "QoS level of publishers (0/1/2).", "0"),
        SUB_QOS("sq", "subqos", true, "QoS level of subscribers (0/1/2).", "0"),
        SH_SUB("ss", "shsub", false, "Enable shared subscription.", null),
        RETAIN("r", "retain", false, "Enable retain.", null),
        TOPIC("t", "topic", true, "Topic name to be used.", "mqttloader-test-topic"),
        PAYLOAD("d", "payload", true, "Data (payload) size in bytes.", "20"),
        NUM_MSG("m", "nmsg", true, "Number of messages sent by each publisher.", "100"),
        RAMP_UP("ru", "rampup", true, "Ramp-up time in seconds.", "0"),
        RAMP_DOWN("rd", "rampdown", true, "Ramp-down time in seconds.", "0"),
        INTERVAL("i", "interval", true, "Publish interval in milliseconds.", "0"),
        SUB_TIMEOUT("st", "subtimeout", true, "Subscribers' timeout in seconds.", "5"),
        EXEC_TIME("et", "exectime", true, "Execution time in seconds.", "60"),
        LOG_LEVEL("l", "log", true, "Log level (SEVERE/WARNING/INFO/ALL).", "INFO"),
        NTP("n", "ntp", true, "NTP server. E.g., ntp.nict.jp", null),
        IN_MEMORY("im", "inmemory", false, "Enable in-memory mode", null),
        USERNAME("un", "username", true, "User name for authentication", null),
        PASSWORD("pw", "password", true, "Password for authentication", null),
        TLS("tl", "tls", false, "Enable TLS", null),
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
