package mqttloader;

import static mqttloader.Loader.countDownLatch;
import static mqttloader.Loader.lastRecvTime;

import java.util.Timer;
import java.util.TimerTask;

public class RecvTimeoutTask extends TimerTask {
    private Timer timer;
    private int subTimeout;

    public RecvTimeoutTask(Timer timer, int subTimeout) {
        this.timer = timer;
        this.subTimeout = subTimeout;
    }

    @Override
    public void run() {
        long remainingTime = subTimeout*1000 - (Loader.getTime() - lastRecvTime);  // <timeout> - <elapsed time>
        if (remainingTime <= 0) {
            countDownLatch.countDown();
        } else {
            timer.schedule(new RecvTimeoutTask(timer, subTimeout), remainingTime);
        }
    }
}
