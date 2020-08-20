package mqttloader;

import static mqttloader.Loader.countDownLatch;
import static mqttloader.Loader.lastRecvTime;

import java.util.Timer;
import java.util.TimerTask;

public class RecvTimeoutTask extends TimerTask {
    private Timer timer;

    public RecvTimeoutTask(Timer timer) {
        this.timer = timer;
    }

    public void run() {
        long remainingTime = Loader.subTimeout*1000 - (Loader.getTime() - lastRecvTime);  // <timeout> - <elapsed time>
        if (remainingTime <= 0) {
            countDownLatch.countDown();
        } else {
            timer.schedule(new RecvTimeoutTask(timer), remainingTime);
        }
    }
}
