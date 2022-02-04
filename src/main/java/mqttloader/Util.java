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

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeMap;
import mqttloader.Constants.Prop;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

public class Util {
    private static final String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
    private static Random random = new Random();

    public static String getPropValue(Prop prop) {
        return Loader.PROPS.getProperty(prop.getName());
    }

    public static int getPropValueInt(Prop prop) {
        return Integer.valueOf(Loader.PROPS.getProperty(prop.getName()));
    }

    public static boolean getPropValueBool(Prop prop) {
        if (Loader.PROPS.getProperty(prop.getName()).equals("true")) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean hasPropValue(Prop prop) {
        if (Loader.PROPS.getProperty(prop.getName()) != null) {
            return true;
        } else {
            return false;
        }
    }

    public static void printHelp(Options options) {
        System.out.println("MQTTLoader version " + Constants.VERSION);
        HelpFormatter help = new HelpFormatter();
        help.setOptionComparator(null);
        help.printHelp(Loader.class.getName(), options, true);
    }

    public static File getAppHomeDir() {
        File file;
        try {
            URL url = Loader.class.getProtectionDomain().getCodeSource().getLocation();
            file = new File(new URL(url.toString()).toURI());
            if(file.getParentFile().getName().equals("lib")){
                file = file.getParentFile().getParentFile();
            } else {
                file = new File("").getAbsoluteFile();
            }
        } catch (SecurityException | NullPointerException | URISyntaxException | MalformedURLException e) {
            file = new File("").getAbsoluteFile();
        }

        return file;
    }

    public static File getDistDir() {
        for(File f1: new File("").getAbsoluteFile().listFiles()) {
            if(f1.getName().equals("src")) {
                for(File f2: f1.listFiles()) {
                    if(f2.getName().equals("dist")) {
                        return f2.getAbsoluteFile();
                    }
                }
            }
        }
        return null;
    }

    public static long getOffsetFromNtpServer() {
        String ntpServer = getPropValue(Prop.NTP);
        long offset = 0;
        if(ntpServer != null) {
            Loader.LOGGER.info("Getting time information from NTP server.");
            NTPUDPClient client = new NTPUDPClient();
            client.setDefaultTimeout(5000);
            InetAddress address = null;
            TimeInfo ti = null;
            try {
                address = InetAddress.getByName(ntpServer);
                client.open();
                ti = client.getTime(address);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(ti != null) {
                ti.computeDetails();
                offset = ti.getOffset();
                Loader.LOGGER.info("Offset is "+offset+" milliseconds.");
            } else {
                Loader.LOGGER.warning("Failed to get time information from NTP server.");
            }
        }

        return offset;
    }

    public static String genRandomChars(int length) {
        StringBuilder sb = new StringBuilder();
        for(int i=0;i<length;i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return new String(sb);
    }

    public static byte[] genPayloads(int size, long currentTime) {
        return ByteBuffer.allocate(size).putLong(currentTime).array();
    }

    public static Instant getCurrentTimeWithOffset() {
        return Instant.now().plusMillis(Loader.offset);
    }

    public static long getEpochMillis(Instant time) {
        return time.getEpochSecond()*1000L + time.getNano()/1000000;
    }

    public static void trimTreeMap(TreeMap<Integer, ?> map, int rampup, int rampdown) {
        if(map.size() == 0) {
            return;
        }
        int firstTime = map.firstKey();
        int lastTime = map.lastKey();
        Iterator<Integer> itr = map.keySet().iterator();
        while(itr.hasNext()){
            int time = itr.next();
            if(time < rampup+firstTime) {
                itr.remove();
            }else if(time > lastTime-rampdown){
                itr.remove();
            }
        }
    }

    public static void paddingTreeMap(TreeMap<Integer, Integer> map) {
        if(map.size() == 0) {
            return;
        }

        for(int i=map.firstKey();i<map.lastKey()+1;i++) {
            if(!map.containsKey(i)) {
                map.put(i, 0);
            }
        }
    }
}
