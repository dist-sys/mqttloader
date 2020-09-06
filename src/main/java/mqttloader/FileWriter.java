package mqttloader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.concurrent.ArrayBlockingQueue;

public class FileWriter implements Runnable {
    private ArrayBlockingQueue<String> queue;

    private File file;
    private FileOutputStream fos = null;
    private OutputStreamWriter osw = null;
    private BufferedWriter bw = null;

    private boolean terminated = false;

    public FileWriter(ArrayBlockingQueue<String> queue, String filename) {
        this.queue = queue;
        file = new File(filename);

        if(file.exists()) {
            file.delete();
        }
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            fos = new FileOutputStream(file, true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        osw = new OutputStreamWriter(fos);
        bw = new BufferedWriter(osw);
    }

    @Override
    public void run() {
        String record = null;
        while (true) {
            if (terminated) {
                break;
            }
            try {
                record = queue.take();
                if(record != null) {
                    bw.write(record);
                    bw.newLine();
                }
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }

        try{
            bw.flush();
            bw.close();
            osw.close();
            fos.close();
        } catch(IOException e){
            e.printStackTrace();
        } finally {
            try {
                if(bw != null) bw.close();
                if(osw != null) osw.close();
                if(fos != null) fos.close();
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }
}
