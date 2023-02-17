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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    public static volatile Instant measurementStartTime = null;
    public static volatile Instant measurementEndTime = null;
    public static volatile Instant lastRecvTime;    // Last time any of subscribers received a message
    public static long offset;

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
        measurementEndTime = Util.getCurrentTimeWithOffset();
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

        prop = Prop.TLS;
        flag = Util.getPropValue(prop);
        if (!flag.equals("true") && !flag.equals("false")) {
            LOGGER.severe("\"" + prop.getName() + "\" in configuration file must be \"true\" or \"false\".");
            exit(1);
        }

        prop = Prop.TLS_ROOTCA_CERT;
        if (Util.hasPropValue(prop)) {
            StringTokenizer st = new StringTokenizer(Util.getPropValue(prop), ";");
            while(st.hasMoreTokens()){
                if(!new File(st.nextToken()).exists()){
                    LOGGER.severe("TLS CA certificate file specified by \"" + prop.getName() + "\" does not exist.");
                    exit(1);
                }
            }
        }

        prop = Prop.TLS_CLIENT_CERT_CHAIN;
        if (Util.hasPropValue(prop)) {
            StringTokenizer st = new StringTokenizer(Util.getPropValue(prop), ";");
            while(st.hasMoreTokens()){
                if(!new File(st.nextToken()).exists()){
                    LOGGER.severe("TLS client certificate file specified by \"" + prop.getName() + "\" does not exist.");
                    exit(1);
                }
            }
        }

        prop = Prop.TLS_CLIENT_KEY;
        if (Util.hasPropValue(prop)) {
            if(!new File(Util.getPropValue(prop)).exists()){
                LOGGER.severe("TLS client key file specified by \"" + prop.getName() + "\" does not exist.");
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
            if(Util.getPropValueBool(Prop.TLS)) {
                broker = Constants.BROKER_PREFIX_TLS +broker;
            } else {
                broker = Constants.BROKER_PREFIX_TCP +broker;
            }
        }

        if(!Util.hasPropValue(Prop.BROKER_PORT)) {
            if(Util.getPropValueBool(Prop.TLS)) {
                broker = broker + ":" + Constants.BROKER_PORT_TLS;
            } else {
                broker = broker + ":" + Constants.BROKER_PORT_TCP;
            }
        } else {
            broker = broker + ":" + Util.getPropValue(Prop.BROKER_PORT);
        }
        LOGGER.info("Broker: " + broker);

        int version = Util.getPropValueInt(Prop.MQTT_VERSION);
        String userName = Util.getPropValue(Prop.USERNAME);
        String password = Util.getPropValue(Prop.PASSWORD);

        Properties sslProps = null;
        if(Util.hasPropValue(Prop.TLS_ROOTCA_CERT)) {
            sslProps = new Properties();
            File trustStoreFile;

            char[] pass;
            FileOutputStream fos;
            try {
                KeyStore trustStore = KeyStore.getInstance("JKS");
                trustStore.load(null, null);
                trustStore.setCertificateEntry("rootCA", new Pem(Util.getPropValue(Prop.TLS_ROOTCA_CERT)).getCerts().get(0));
                
                pass = Util.genRandomChars(Constants.KEYSTORE_PASSWORD_LENGTH).toCharArray(); 
                trustStoreFile = File.createTempFile("ml-tmp-truststore", ".jks");
                trustStoreFile.deleteOnExit();
                fos = new FileOutputStream(trustStoreFile);
                trustStore.store(fos, pass);
                fos.flush();
                fos.close();
            } catch (Exception e) {
                throw new RuntimeException("Cannot build keystore", e);
            }
            sslProps.setProperty("com.ibm.ssl.trustStore", trustStoreFile.getPath());
            sslProps.setProperty("com.ibm.ssl.trustStorePassword", new String(pass));
            LOGGER.info("Truststore: "+trustStoreFile.getAbsolutePath());
//            LOGGER.info("Truststore pass: "+new String(pass));

            if(Util.hasPropValue(Prop.TLS_CLIENT_KEY)) {
                File keyStoreFile;            
                try {
                    PrivateKey key = new Pem(Util.getPropValue(Prop.TLS_CLIENT_KEY)).getPrivateKey();

                    // Note: StringTokenizer skips an empty token.
                    StringTokenizer st = new StringTokenizer(Util.getPropValue(Prop.TLS_CLIENT_CERT_CHAIN), ";");
                    Certificate[] certArray = new Certificate[st.countTokens()];
                    for(int i=0;i<certArray.length;i++){
                        certArray[i] = new Pem(st.nextToken()).getCerts().get(0);
                    }

                    KeyStore keyStore = KeyStore.getInstance("JKS");
                    keyStore.load(null, null);
        
                    pass = Util.genRandomChars(Constants.KEYSTORE_PASSWORD_LENGTH).toCharArray(); 
                    keyStoreFile = File.createTempFile("ml-tmp-keystore", ".jks");
                    keyStoreFile.deleteOnExit();
                    keyStore.setKeyEntry("client-cert", key, pass, certArray);
                    fos = new FileOutputStream(keyStoreFile);
                    keyStore.store(fos, pass);
                    fos.flush();
                    fos.close();

                    sslProps.setProperty("com.ibm.ssl.keyStore", keyStoreFile.getPath());
                    sslProps.setProperty("com.ibm.ssl.clientAuthentication", "true");
                    sslProps.setProperty("com.ibm.ssl.keyStorePassword", new String(pass));
                    LOGGER.info("Keystore: "+keyStoreFile.getAbsolutePath());
//                    LOGGER.info("Keystore pass: "+new String(pass));
                } catch (Exception e) {
                    throw new RuntimeException("Cannot build keystore", e);
                }
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
        offset = Util.getOffsetFromNtpServer();

        // delay: Give ScheduledExecutorService time to setup scheduling.
        long delay = publishers.size();

        measurementStartTime = Util.getCurrentTimeWithOffset().plusMillis(delay);
        lastRecvTime = measurementStartTime;

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
            timer.schedule(new RecvTimeoutTask(timer, subTimeout), subTimeout*Constants.SECOND_IN_MILLI);
        }

        int execTime = Util.getPropValueInt(Prop.EXEC_TIME);
        execTime -= (int)(Duration.between(Loader.measurementStartTime, Util.getCurrentTimeWithOffset()).get(ChronoUnit.SECONDS));
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
					if(!st.hasMoreTokens()) {
						continue; // skip the first line that only has a timestamp.
					}
                    int elapsedTime = (int)((timestamp - Util.getEpochMicros(Loader.measurementStartTime))/Constants.SECOND_IN_MICRO);
                    st.nextToken(); //client ID
                    boolean isSend = st.nextToken().equals("S") ? true : false;
                    if(isSend){
                        recorder.recordSendInMemory(elapsedTime);
                    } else {
                        recorder.recordReceiveInMemory(elapsedTime, Long.valueOf(st.nextToken()));
                    }
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
        TreeMap<Integer, Long> latencyMaxs = recorder.getLatencyMaxs();

        int rampup = Util.getPropValueInt(Prop.RAMP_UP);
        int rampdown = Util.getPropValueInt(Prop.RAMP_DOWN);

        Util.trimTreeMap(sendThroughputs, rampup, rampdown);
        Util.trimTreeMap(recvThroughputs, rampup, rampdown);
        Util.trimTreeMap(latencySums, rampup, rampdown);
        Util.trimTreeMap(latencyMaxs, rampup, rampdown);

        Util.paddingTreeMap(sendThroughputs);
        Util.paddingTreeMap(recvThroughputs);

        System.out.println();
        System.out.println("Measurement started: " + Constants.DATE_FORMAT_FOR_LOG.format(Date.from(measurementStartTime)));
        System.out.println("Measurement ended: " + Constants.DATE_FORMAT_FOR_LOG.format(Date.from(measurementEndTime)));
        System.out.println();
        System.out.println("-----Publisher-----");
        printThroughput(sendThroughputs, true);
        System.out.println();
        System.out.println("-----Subscriber-----");
        printThroughput(recvThroughputs, false);

        long maxLtMicros = 0;
        double aveLtMicros = 0;
        long numMsg = 0;
        for(int elapsedSecond: latencySums.keySet()) {
            if(latencyMaxs.get(elapsedSecond) > maxLtMicros) {
                maxLtMicros = latencyMaxs.get(elapsedSecond);
            }
            int numInSec = recvThroughputs.get(elapsedSecond);
            numMsg += numInSec;
            double aveInSec = (double)latencySums.get(elapsedSecond)/numInSec;
            aveLtMicros = aveLtMicros + ((aveInSec-aveLtMicros)*numInSec)/numMsg;
        }

        double maxLtMillis = (double)maxLtMicros/Constants.MILLISECOND_IN_MICRO;
        double aveLtMillis = aveLtMicros/Constants.MILLISECOND_IN_MICRO;

        System.out.println("Maximum latency [ms]: "+String.format("%.3f", maxLtMillis));
        System.out.println("Average latency [ms]: "+String.format("%.3f", aveLtMillis));
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
        System.out.println("Maximum throughput [msg/s]: "+maxTh);
        System.out.println("Average throughput [msg/s]: "+String.format("%.3f", aveTh));
        if(forPublisher){
            System.out.println("Number of published messages: "+sumMsg);
        }else{
            System.out.println("Number of received messages: "+sumMsg);
        }

        System.out.print("Per second throughput [msg/s]: ");
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
