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
    public static final String VERSION = "0.8.6";
    public static final String BROKER_PREFIX_TCP = "tcp://";
    public static final String BROKER_PREFIX_TLS = "ssl://";
    public static final String BROKER_PORT_TCP = "1883";
    public static final String BROKER_PORT_TLS = "8883";
    public static final String FILE_NAME_PREFIX = "mqttloader_";
    private static final String HOST_ID = Util.genRandomChars(4);
    public static final String SUB_CLIENT_ID_PREFIX = "ml-"+HOST_ID+"-s-";
    public static final String PUB_CLIENT_ID_PREFIX = "ml-"+HOST_ID+"-p-";
    public static final Record STOP_SIGNAL = new Record();
    public static final int MICROSECOND_IN_NANO = 1000;
    public static final int MILLISECOND_IN_MICRO = 1000;
    public static final long SECOND_IN_MILLI = 1000L;
    public static final long SECOND_IN_MICRO = 1000000L;
    public static final SimpleDateFormat DATE_FORMAT_FOR_LOG = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z");
    public static final SimpleDateFormat DATE_FORMAT_FOR_FILENAME = new SimpleDateFormat("yyyyMMdd-HHmmss");
    public static final int KEYSTORE_PASSWORD_LENGTH = 20;

    public enum Opt {
        CONFIG("c", true, "Configuration file's path.", "mqttloader.conf"),
        HELP("h", false, "Display help.");

        private String name;
        private boolean hasArg;
        private boolean required;
        private String description;
        private String defaultValue;

        private Opt(String name, boolean hasArg, String description, String defaultValue) {
            this(name, hasArg, description, defaultValue, false);
        }

        private Opt(String name, boolean hasArg, String description) {
            this(name, hasArg, description, null, false);
        }

        private Opt(String name, boolean hasArg, String description, String defaultValue, boolean required) {
            this.name = name;
            this.hasArg = hasArg;
            this.description = description;
            this.defaultValue = defaultValue;
            this.required = required;
        }

        public String getName() {
            return name;
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

    public enum Prop {
        BROKER("broker"),
        BROKER_PORT("broker_port"),  // Default value is configured not here but in Loader class, because it has different default values according to protocol.
        MQTT_VERSION("mqtt_version", "5"),
        NUM_PUB("num_publishers", "1"),
        NUM_SUB("num_subscribers", "1"),
        QOS_PUB("qos_publisher", "0"),
        QOS_SUB("qos_subscriber", "0"),
        SHARED_SUB("shared_subscription", "false"),
        RETAIN("retain", "false"),
        TOPIC("topic", "mqttloader-test-topic"),
        PAYLOAD("payload", "20"),
        NUM_MSG("num_messages", "100"),
        RAMP_UP("ramp_up", "0"),
        RAMP_DOWN("ramp_down", "0"),
        INTERVAL("interval", "0"),
        SUB_TIMEOUT("subscriber_timeout", "5"),
        EXEC_TIME("exec_time", "60"),
        LOG_LEVEL("log_level", "INFO"),
        NTP("ntp"),
        OUTPUT("output"),
        USERNAME("user_name"),
        PASSWORD("password"),
        TLS("tls", "false"),
        TLS_ROOTCA_CERT("tls_rootca_cert"),
        TLS_CLIENT_CERT_CHAIN("tls_client_cert_chain"),
        TLS_CLIENT_KEY("tls_client_key");

        private final String name;
        private final String defaultValue;

        Prop(String name) {
            this(name, null);
        }

        Prop(String name, String defaultValue) {
            this.name = name;
            this.defaultValue = defaultValue;
        }

        public String getName() {
            return name;
        }

        public String getDefaultValue() {
            return defaultValue;
        }
    }

    public enum PemFormat {
        PKCS1("-----BEGIN RSA PRIVATE KEY-----", "-----END RSA PRIVATE KEY-----"),
        PKCS8("-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----"),
        RFC5915("-----BEGIN EC PRIVATE KEY-----", "-----END EC PRIVATE KEY-----"),
        RFC7468_CERT("-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----"),
        X509("-----BEGIN X509 CERTIFICATE-----", "-----END X509 CERTIFICATE-----");

        private final String begin;
        private final String end;

        PemFormat(String begin, String end){
            this.begin = begin;
            this.end = end;
        }

        public String getBegin(){
            return begin;
        }

        public String getEnd(){
            return end;            
        }
    }
}