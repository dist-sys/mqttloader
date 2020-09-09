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

import java.nio.ByteBuffer;
import java.util.Random;

public class Util {
    private static final String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
    private static Random random = new Random();

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
}
