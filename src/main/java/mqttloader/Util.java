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

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeMap;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

public class Util {
    private static final String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
    private static Random random = new Random();

    public static String getOptVal(Constants.Opt opt) {
        return Loader.cmd.getOptionValue(opt.getName(), opt.getDefaultValue());
    }

    public static int getOptValInt(Constants.Opt opt) {
        return Integer.valueOf(Loader.cmd.getOptionValue(opt.getName(), opt.getDefaultValue()));
    }

    public static boolean hasOpt(Constants.Opt opt) {
        return Loader.cmd.hasOption(opt.getName());
    }

    public static void printHelp(Options options) {
        System.out.println("MQTTLoader version " + Constants.VERSION);
        HelpFormatter help = new HelpFormatter();
        help.setOptionComparator(null);
        help.printHelp(Loader.class.getName(), options, true);
    }

    public static long getOffsetFromNtpServer() {
        String ntpServer = getOptVal(Constants.Opt.NTP);
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

    public static long getCurrentTimeMillis() {
        return (getElapsedNanoTime()/Constants.MILLISECOND_IN_NANO) + Loader.startTime;
    }

    /**
     *
     * @return Elapsed time from startTime in nano seconds.
     */
    public static long getElapsedNanoTime() {
        return System.nanoTime() - Loader.startNanoTime;
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
