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

import static java.lang.System.exit;
import static mqttloader.Constants.Opt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import mqttloader.client.AbstractClient;
import mqttloader.client.AbstractPublisher;
import mqttloader.client.PublisherV5;
import mqttloader.client.PublisherV3;
import mqttloader.client.SubscriberV5;
import mqttloader.client.SubscriberV3;
import mqttloader.Constants.Prop;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Loader {
    private File confFile = null;
    public static Properties PROPS;
    private final List<AbstractClient> publishers = new ArrayList<>();
    private final List<AbstractClient> subscribers = new ArrayList<>();

    public static volatile long startTime = 0;  // Measurement start time by System.currentTimeMillis()
    public static volatile long startNanoTime = 0;  // Measurement start time by System.nanoTime()
    private long endTime;  // Measurement end time
    public static volatile long lastRecvTime;   // Last time any of subscribers received a message

    private Recorder recorder;
    public static CountDownLatch cdl;

    public static final Logger LOGGER = Logger.getLogger(Loader.class.getName());

    public Loader(String[] args) {
        PROPS = new Properties(getDefaultProperties());
        loadCommandLineArguments(args);
        loadConfigurationFile();

        LOGGER.setLevel(Level.parse(Util.getPropValue(Prop.LOG_LEVEL)));
        LOGGER.info("MQTTLoader version " + Constants.VERSION + " starting.");
        LOGGER.info("Configuration file: " + confFile.getAbsolutePath());

        initFields();

        LOGGER.info("Preparing clients.");
        prepareClients();
        recorder.start();

        LOGGER.info("Starting measurement.");
        startMeasurement();
        waitForMeasurement();

        LOGGER.info("Terminating clients.");
        disconnectClients();
        endTime = Util.getCurrentTimeMillis();
        recorder.terminate();

        LOGGER.info("Calculating results.");
        calcResult();
    }

    private Properties getDefaultProperties() {
        Properties props = new Properties();
        for (Prop prop: Prop.values()) {
            if (prop.getDefaultValue() != null) {
                props.setProperty(prop.getName(), prop.getDefaultValue());
            }
        }
        return props;
    }

    /**
     * Load command-line arguments.
     * @param args Command-line arguments.
     */
    private void loadCommandLineArguments(String... args) {
        Options options = new Options();
        for(Opt opt: Opt.values()){
            if(opt.isRequired()){
                options.addRequiredOption(opt.getName(), null, opt.hasArg(), opt.getDescription());
            }else{
                options.addOption(opt.getName(), opt.hasArg(), opt.getDescription());
            }
        }

        for(String arg: args){
            if(arg.equals("-"+Opt.HELP.getName())){
                Util.printHelp(options);
                exit(0);
            }
        }

        CommandLine cmd = null;
        CommandLineParser parser = new DefaultParser();
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            LOGGER.severe("Failed to parse options.");
            Util.printHelp(options);
            exit(1);
        }

        if (cmd.hasOption(Opt.CONFIG.getName())){
            confFile = new File(cmd.getOptionValue(Opt.CONFIG.getName()));
        } else {
            File dir = Util.getDistDir();
            if (dir==null) {
                dir = Util.getAppHomeDir();
            }
            confFile = new File(dir, Opt.CONFIG.getDefaultValue());
        }

        if (!confFile.exists()) {
            LOGGER.severe("Unable to find config file.");
            exit(1);
        }
    }

    /**
     * Load parameters from configuration file.
     */
    private void loadConfigurationFile() {
        try {
            PROPS.load(new FileInputStream(confFile));
        } catch (FileNotFoundException e) {
            LOGGER.severe("Unable to find config file.");
            exit(1);
        } catch (IOException e) {
            LOGGER.severe("Unable to open config file.");
            exit(1);
            return;
        }

        // Validate arguments.
        Prop prop = Prop.SHARED_SUB;
        String flag = Util.getPropValue(prop);
        if (!flag.equals("true") && !flag.equals("false")) {
            LOGGER.severe("\"" + prop.getName() + "\" in configuration file must be \"true\" or \"false\".");
            exit(1);
        }

        prop = Prop.RETAIN;
        flag = Util.getPropValue(prop);
        if (!flag.equals("true") && !flag.equals("false")) {
            LOGGER.severe("\"" + prop.getName() + "\" in configuration file must be \"true\" or \"false\".");
            exit(1);
        }

        prop = Prop.OUTPUT;
        if (Util.hasPropValue(prop)) {
            File dir = new File(Util.getPropValue(prop));
            if (dir.isFile()) {
                LOGGER.severe("\"" + prop.getName() + "\" in configuration file must be a directory.");
                exit(1);
            }
        }

        prop = Prop.MQTT_VERSION;
        int version = Util.getPropValueInt(prop);
        if(version != 3 && version != 5) {
            LOGGER.severe("\"" + prop.getName() + "\" in configuration file must be 3 or 5.");
            exit(1);
        }

        prop = Prop.QOS_PUB;
        int pubqos = Util.getPropValueInt(prop);
        if(pubqos != 0 && pubqos != 1 && pubqos != 2) {
            LOGGER.severe("\"" + prop.getName() + "\" in configuration file must be 0 or 1 or 2.");
            exit(1);
        }

        prop = Prop.QOS_SUB;
        int subqos = Util.getPropValueInt(prop);
        if(subqos != 0 && subqos != 1 && subqos != 2) {
            LOGGER.severe("\"" + prop.getName() + "\" in configuration file must be 0 or 1 or 2.");
            exit(1);
        }

        prop = Prop.PAYLOAD;
        if(Util.getPropValueInt(prop) < 8) {
            LOGGER.severe("\"" + prop.getName() + "\" in configuration file must be equal to or larger than 8.");
            exit(1);
        }

        prop = Prop.TLS_TRUSTSTORE;
        if (Util.hasPropValue(prop)) {
            if (!Util.hasPropValue(Prop.TLS_TRUSTSTORE_PASS)) {
                LOGGER.severe("\"" + Prop.TLS_TRUSTSTORE_PASS.getName() + "\" in configuration file is mandatory if \"" + prop.getName() + "\" is specified.");
                exit(1);
            }
            if(!new File(Util.getPropValue(prop)).exists()){
                LOGGER.severe("TLS truststore file specified by \"" + prop.getName() + "\" does not exist.");
                exit(1);
            }
        }

        prop = Prop.TLS_TRUSTSTORE_PASS;
        if (Util.hasPropValue(prop)) {
            if (!Util.hasPropValue(Prop.TLS_TRUSTSTORE)) {
                LOGGER.severe("\"" + Prop.TLS_TRUSTSTORE.getName() + "\" in configuration file is mandatory if \"" + Prop.TLS_TRUSTSTORE_PASS.getName() + "\" is specified.");
                exit(1);
            }
        }

        prop = Prop.TLS_KEYSTORE;
        if (Util.hasPropValue(prop)) {
            if (!Util.hasPropValue(Prop.TLS_TRUSTSTORE) || !Util.hasPropValue(Prop.TLS_TRUSTSTORE_PASS) || !Util.hasPropValue(Prop.TLS_KEYSTORE_PASS)) {
                LOGGER.severe("\"" + Prop.TLS_TRUSTSTORE.getName() + "\", \"" + Prop.TLS_TRUSTSTORE_PASS.getName() + "\", and \"" + Prop.TLS_KEYSTORE_PASS.getName() + "\" in configuration file is mandatory if \"" + prop.getName() + "\" is specified.");
                exit(1);
            }
            if(!new File(Util.getPropValue(prop)).exists()){
                LOGGER.severe("TLS keystore file specified by \"" + prop.getName() + "\" does not exist.");
                exit(1);
            }
        }

        prop = Prop.TLS_KEYSTORE_PASS;
        if (Util.hasPropValue(prop)) {
            if (!Util.hasPropValue(Prop.TLS_TRUSTSTORE) || !Util.hasPropValue(Prop.TLS_TRUSTSTORE_PASS) || !Util.hasPropValue(Prop.TLS_KEYSTORE)) {
                LOGGER.severe("\"" + Prop.TLS_TRUSTSTORE.getName() + "\", \"" + Prop.TLS_TRUSTSTORE_PASS.getName() + "\", and \"" + Prop.TLS_KEYSTORE.getName() + "\" in configuration file is mandatory if \"" + prop.getName() + "\" is specified.");
                exit(1);
            }
        }
    }

    /**
     * Initialize fields that needs parameter values.
     */
    private void initFields() {
        // If there is one or more subscriber(s), need to wait for subscribers' timeout in addition with publishers' completion.
        cdl = Util.getPropValueInt(Prop.NUM_SUB) > 0 ? new CountDownLatch(Util.getPropValueInt(Prop.NUM_PUB)+1) : new CountDownLatch(Util.getPropValueInt(Prop.NUM_PUB));
        recorder = new Recorder(getRecFile(), !Util.hasPropValue(Prop.OUTPUT));
    }

    /**
     * Prepare MQTT clients and make them connect to the broker.
     */
    private void prepareClients() {
        String broker = Util.getPropValue(Prop.BROKER);
        if(!broker.startsWith(Constants.BROKER_PREFIX_TCP) && !broker.startsWith(Constants.BROKER_PREFIX_TLS)) {
            if(!Util.hasPropValue(Prop.TLS_TRUSTSTORE)) {
                broker = Constants.BROKER_PREFIX_TCP +broker;
            } else {
                broker = Constants.BROKER_PREFIX_TLS +broker;
            }
        }

        if(!Util.hasPropValue(Prop.BROKER_PORT)) {
            if (!Util.hasPropValue(Prop.TLS_TRUSTSTORE)) {
                broker = broker + ":" + Constants.BROKER_PORT_TCP;
            } else {
                broker = broker + ":" + Constants.BROKER_PORT_TLS;
            }
        } else {
            broker = broker + ":" + Util.getPropValue(Prop.BROKER_PORT);
        }
        LOGGER.info("Broker: " + broker);

        int version = Util.getPropValueInt(Prop.MQTT_VERSION);
        String userName = Util.getPropValue(Prop.USERNAME);
        String password = Util.getPropValue(Prop.PASSWORD);

        Properties sslProps = null;
        if(Util.hasPropValue(Prop.TLS_TRUSTSTORE)) {
            sslProps = new Properties();
            File trustStore = new File(Util.getPropValue(Prop.TLS_TRUSTSTORE));
            sslProps.setProperty("com.ibm.ssl.trustStore", trustStore.getPath());
            sslProps.setProperty("com.ibm.ssl.trustStorePassword", Util.getPropValue(Prop.TLS_TRUSTSTORE_PASS));
            LOGGER.info("Truststore file: "+trustStore.getAbsolutePath());
            if(Util.hasPropValue(Prop.TLS_KEYSTORE)) {
                File keystore = new File(Util.getPropValue(Prop.TLS_KEYSTORE));
                sslProps.setProperty("com.ibm.ssl.keyStore", keystore.getPath());
                sslProps.setProperty("com.ibm.ssl.clientAuthentication", "true");
                sslProps.setProperty("com.ibm.ssl.keyStorePassword", Util.getPropValue(Prop.TLS_KEYSTORE_PASS));
                LOGGER.info("Keystore file: "+keystore.getAbsolutePath());
            }
        }

        int numPub = Util.getPropValueInt(Prop.NUM_PUB);
        int numSub = Util.getPropValueInt(Prop.NUM_SUB);
        int pubQos = Util.getPropValueInt(Prop.QOS_PUB);
        int subQos = Util.getPropValueInt(Prop.QOS_SUB);
        boolean shSub = Util.getPropValueBool(Prop.SHARED_SUB);
        boolean retain = Util.getPropValueBool(Prop.RETAIN);
        String topic = Util.getPropValue(Prop.TOPIC);
        int payloadSize = Util.getPropValueInt(Prop.PAYLOAD);
        int numMessage = Util.getPropValueInt(Prop.NUM_MSG);
        int pubInterval = Util.getPropValueInt(Prop.INTERVAL);
        for(int i=0;i<numPub;i++){
            if(version==5){
                publishers.add(new PublisherV5(i, broker, userName, password, sslProps, pubQos, retain, topic, payloadSize, numMessage, pubInterval, recorder));
            }else{
                publishers.add(new PublisherV3(i, broker, userName, password, sslProps, pubQos, retain, topic, payloadSize, numMessage, pubInterval, recorder));
            }
        }

        for(int i=0;i<numSub;i++){
            if(version==5){
                subscribers.add(new SubscriberV5(i, broker, userName, password, sslProps, subQos, shSub, topic, recorder));
            }else{
                subscribers.add(new SubscriberV3(i, broker, userName, password, sslProps, subQos, topic, recorder));
            }
        }
    }

    /**
     * Obtain a File object to be used to store sending/receiving records by Recorder instance.
     * @return File instance. NULL if the parameter "-im" is specified.
     */
    private File getRecFile() {
        File file = null;
        if (Util.hasPropValue(Prop.OUTPUT)) {
            File dir = new File(Util.getPropValue(Prop.OUTPUT));
            if (!dir.exists()) {
                dir.mkdirs();
            }
//            String date = Constants.DATE_FORMAT_FOR_FILENAME.format(new Date(System.currentTimeMillis() + offset));
            String date = Constants.DATE_FORMAT_FOR_FILENAME.format(new Date(System.currentTimeMillis()));
            file = new File(dir, Constants.FILE_NAME_PREFIX+date+".csv");

            if(file.exists()) {
                file.delete();
            }
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

            LOGGER.info("Output file placed at: " + file.getAbsolutePath());
        }

        return file;
    }

    /**
     * Start measurement by running publishers.
     */
    private void startMeasurement() {
        // delay: Give ScheduledExecutorService time to setup scheduling.
        long delay = publishers.size();
        long offset = Util.getOffsetFromNtpServer();
        long currentTime = System.currentTimeMillis();
        long currentNanoTime = System.nanoTime();

        startTime = currentTime + offset + delay;
        startNanoTime = currentNanoTime + delay * Constants.MILLISECOND_IN_NANO;
        lastRecvTime = startTime;

        for(AbstractClient pub: publishers){
            ((AbstractPublisher)pub).start(delay);
        }
    }

    /**
     * Wait for completion of measurement.
     * This method waits until all publishers complete sending PUBLISH messages and the time specified by parameter "-st" elapses since all subscribers last received a message.
     * Nevertheless, when the time specified by parameter "-et" elapses, it goes into a timeout.
     */
    private void waitForMeasurement() {
        Timer timer = null;
        if(Util.getPropValueInt(Prop.NUM_SUB) > 0){
            timer = new Timer();
            int subTimeout = Util.getPropValueInt(Prop.SUB_TIMEOUT);
            timer.schedule(new RecvTimeoutTask(timer, subTimeout), subTimeout*1000);
        }

        int execTime = Util.getPropValueInt(Prop.EXEC_TIME);
        execTime -= (int)(Util.getElapsedNanoTime()/Constants.SECOND_IN_NANO);
        if(execTime > 0) {
            try {
                cdl.await(execTime, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if(cdl.getCount()>0) {
            LOGGER.info("Measurement timed out.");
        } else {
            LOGGER.info("Measurement completed.");
        }

        if (timer != null) {
            timer.cancel();
        }
    }

    /**
     * Disconnect MQTT clients from the broker.
     */
    private void disconnectClients() {
        for(AbstractClient pub: publishers) {
            pub.disconnect();
        }
        for(AbstractClient sub: subscribers) {
            sub.disconnect();
        }
    }

    /**
     * Calculate the measurement result.
     */
    private void calcResult() {
        if(Util.hasPropValue(Prop.OUTPUT)) {
            FileInputStream fis = null;
            InputStreamReader isr = null;
            BufferedReader br = null;
            try{
                fis = new FileInputStream(recorder.getFile());
                isr = new InputStreamReader(fis);
                br = new BufferedReader(isr);

                String str;
                while ((str = br.readLine()) != null) {
                    StringTokenizer st = new StringTokenizer(str, ",");
                    long timestamp = Long.valueOf(st.nextToken());
                    String clientId = st.nextToken(); //client ID
                    boolean isSend = st.nextToken().equals("S") ? true : false;
                    int latency = -1;
                    if (st.hasMoreTokens()) {
                        latency = Integer.valueOf(st.nextToken());
                    }

                    recorder.recordInMemory(new Record(timestamp, clientId, isSend, latency));
                }

                br.close();
                isr.close();
                fis.close();
            } catch(IOException e){
                e.printStackTrace();
            } finally {
                try {
                    if(br != null) br.close();
                    if(isr != null) isr.close();
                    if(fis != null) fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }

        TreeMap<Integer, Integer> sendThroughputs = recorder.getSendThroughputs();
        TreeMap<Integer, Integer> recvThroughputs = recorder.getRecvThroughputs();
        TreeMap<Integer, Long> latencySums = recorder.getLatencySums();
        TreeMap<Integer, Integer> latencyMaxs = recorder.getLatencyMaxs();

        int rampup = Util.getPropValueInt(Prop.RAMP_UP);
        int rampdown = Util.getPropValueInt(Prop.RAMP_DOWN);

        Util.trimTreeMap(sendThroughputs, rampup, rampdown);
        Util.trimTreeMap(recvThroughputs, rampup, rampdown);
        Util.trimTreeMap(latencySums, rampup, rampdown);
        Util.trimTreeMap(latencyMaxs, rampup, rampdown);

        Util.paddingTreeMap(sendThroughputs);
        Util.paddingTreeMap(recvThroughputs);

        System.out.println();
        System.out.println("Measurement started: " + Constants.DATE_FORMAT_FOR_LOG.format(new Date(startTime)));
        System.out.println("Measurement ended: " + Constants.DATE_FORMAT_FOR_LOG.format(new Date(endTime)));
        System.out.println();
        System.out.println("-----Publisher-----");
        printThroughput(sendThroughputs, true);
        System.out.println();
        System.out.println("-----Subscriber-----");
        printThroughput(recvThroughputs, false);

        int maxLt = 0;
        double aveLt = 0;
        long numMsg = 0;
        for(int elapsedSecond: latencySums.keySet()) {
            if(latencyMaxs.get(elapsedSecond) > maxLt) {
                maxLt = latencyMaxs.get(elapsedSecond);
            }
            int numInSec = recvThroughputs.get(elapsedSecond);
            numMsg += numInSec;
            double aveInSec = (double)latencySums.get(elapsedSecond)/numInSec;
            aveLt = aveLt + ((aveInSec-aveLt)*numInSec)/numMsg;
        }

        System.out.println("Maximum latency[ms]: "+maxLt);
        System.out.println("Average latency[ms]: "+String.format("%.2f", aveLt));
    }

    /**
     * Print out throughput result to console.
     * @param throughputs Map object storing throughput data. keys are the elapsed seconds from the measurement start time, and values are the number of messages for that one second.
     * @param forPublisher True if it is the publisher-side throughput. False if it is the subscriber-side throughput.
     */
    private void printThroughput(TreeMap<Integer, Integer> throughputs, boolean forPublisher) {
        int maxTh = 0;
        int sumMsg = 0;
        for(int elapsedSecond: throughputs.keySet()){
            int th = throughputs.get(elapsedSecond);
            if(th > maxTh) {
                maxTh = th;
            }
            sumMsg += th;
        }

        double aveTh = throughputs.size()>0 ? (double)sumMsg/throughputs.size() : 0;
        System.out.println("Maximum throughput[msg/s]: "+maxTh);
        System.out.println("Average throughput[msg/s]: "+String.format("%.2f", aveTh));
        if(forPublisher){
            System.out.println("Number of published messages: "+sumMsg);
        }else{
            System.out.println("Number of received messages: "+sumMsg);
        }

        System.out.print("Per second throughput[msg/s]: ");
        for(int elapsedSecond: throughputs.keySet()){
            System.out.print(throughputs.get(elapsedSecond));
            if(elapsedSecond<throughputs.lastKey()){
                System.out.print(", ");
            }
        }
        System.out.println();
    }

    public static void main(String[] args){
        new Loader(args);
    }
}
