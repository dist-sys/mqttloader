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

import static mqttloader.Loader.cdl;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Timer;
import java.util.TimerTask;

public class RecvTimeoutTask extends TimerTask {
    private Timer timer;
    private int subTimeout;

    /**
     *
     * @param timer Timer instance for this task.
     * @param subTimeout Timeout value in second.
     */
    public RecvTimeoutTask(Timer timer, int subTimeout) {
        this.timer = timer;
        this.subTimeout = subTimeout;
    }

    @Override
    public void run() {
        long remainingTime = subTimeout - Duration.between(Loader.lastRecvTime, Util.getCurrentTimeWithOffset()).get(ChronoUnit.SECONDS);  // <timeout> - <elapsed time>
        if (remainingTime <= 0) {
            Loader.LOGGER.info("Subscribers timed out.");
            cdl.countDown();
        } else {
            timer.schedule(new RecvTimeoutTask(timer, subTimeout), remainingTime*Constants.SECOND_IN_MILLI);
        }
    }
}
