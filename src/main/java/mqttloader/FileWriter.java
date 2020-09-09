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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class FileWriter implements Runnable {
    private File file;
    private FileOutputStream fos = null;
    private OutputStreamWriter osw = null;
    private BufferedWriter bw = null;

    public FileWriter(File file) {
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
            try {
                record = Loader.queue.take();
                if(record != null) {
                    if(record.equals(Constants.STOP_SIGNAL)) {
                        break;
                    }
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
