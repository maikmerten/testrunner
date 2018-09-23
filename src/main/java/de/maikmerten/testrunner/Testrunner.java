package de.maikmerten.testrunner;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author merten
 */
public class Testrunner {

    private class WaitThread extends Thread {

        private final Process p;
        public boolean running = true;

        public WaitThread(Process p) {
            this.p = p;
        }

        @Override
        public void run() {
            try {
                p.waitFor();
                running = false;
            } catch (InterruptedException ex) {
                Logger.getLogger(Testrunner.class.getName()).log(Level.SEVERE, "Exception when waiting for process", ex);
            }
        }
    }

    private class TestThread extends Thread {

        private final Queue<TestDescription> queue;

        public TestThread(Queue<TestDescription> q) {
            this.queue = q;
        }

        @Override
        public void run() {
            TestDescription test;
            while (true) {
                try {
                    synchronized (queue) {
                        test = queue.poll();
                    }
                    if (test == null) {
                        break;
                    }
                    runTest(test);
                } catch (Exception ex) {
                    Logger.getLogger(Testrunner.class.getName()).log(Level.SEVERE, "Exception in testing thread", ex);
                }
            }
        }
    }

    private final static String SSIM = "dump_ssim -s ORIGINAL DECODED";

    private final Map<String, List<LogEntry>> logs = new HashMap<>();

    private String execCmd(String cmd) throws Exception {
        System.out.println("Running cmd: " + cmd);
        Process p = Runtime.getRuntime().exec(cmd);
        BufferedReader error = new BufferedReader(new InputStreamReader(p.getErrorStream()));
        BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream()));

        WaitThread wt = new WaitThread(p);
        wt.start();

        String lastline = null;

        // read stderr and stdout
        while (wt.running) {
            Thread.sleep(100);
            if (error.ready()) {
                String line = error.readLine();
                //System.out.println(line);
            }
            if (out.ready()) {
                lastline = out.readLine();
                //System.out.println(lastline);
            }
        }

        // just make sure we really did not miss any output on stdout
        while (out.ready()) {
            lastline = out.readLine();
        }

        wt.join();
        return lastline;
    }

    private void addToLog(String key, LogEntry entry) {
        synchronized (logs) {
            List<LogEntry> log;
            if (logs.get(key) == null) {
                log = new ArrayList<>();
                logs.put(key, log);
            }
            log = logs.get(key);
            log.add(entry);
        }
    }

    private void runTest(TestDescription test) throws Exception {
        int kbits = test.kbitstart;
        int bits = kbits * 1000;
        StringBuilder sb = new StringBuilder();

        String quantizer = "" + test.quantstart;
        if (quantizer.endsWith(".0")) {
            quantizer = quantizer.substring(0, quantizer.lastIndexOf("."));
        }

        File infile = test.input;
        if (!infile.exists()) {
            throw new RuntimeException("Cannot find " + infile.getAbsolutePath());
        }

        if (test.quantinc <= 0) {
            System.out.println("Starting test of " + test.codecname + " with " + kbits + " kbit/s");
            sb.append("\nResults for ").append(test.codecname).append(" at ").append(kbits).append(" kbps:\n");
        } else {
            System.out.println("Starting test of " + test.codecname + " with quantizer " + quantizer);
            sb.append("\nResults for ").append(test.codecname).append(" with quantizer ").append(quantizer).append("\n");
        }
        

        File encfile = File.createTempFile("encoding", test.suffix);
        File decfile = File.createTempFile("decoded", ".y4m");
        File passfile = File.createTempFile("pass", ".log");

        Date start = new Date();

        try {
            // Encode input file
            String cmd = test.pass1;
            cmd = cmd.replaceAll("INFILE", infile.getAbsolutePath());
            cmd = cmd.replaceAll("OUTFILE", encfile.getAbsolutePath());
            cmd = cmd.replaceAll("PASSFILE", passfile.getAbsolutePath());
            cmd = cmd.replaceAll("KBITS", "" + kbits);
            cmd = cmd.replaceAll("BITS", "" + bits);
            cmd = cmd.replaceAll("QUANTIZER", "" + quantizer);
            execCmd(cmd);

            if (test.pass2 != null) {
                cmd = test.pass2;
                cmd = cmd.replaceAll("INFILE", infile.getAbsolutePath());
                cmd = cmd.replaceAll("OUTFILE", encfile.getAbsolutePath());
                cmd = cmd.replaceAll("PASSFILE", passfile.getAbsolutePath());
                cmd = cmd.replaceAll("KBITS", "" + kbits);
                cmd = cmd.replaceAll("BITS", "" + bits);
                execCmd(cmd);
            }

            Date end = new Date();
            long enctime = end.getTime() - start.getTime();

            long encsize = encfile.length();
            sb.append("Encoded file size: ").append(encsize).append("\n");

            // compute actual bitrate
            double bitrate = (encsize * 8.0 / test.inputduration) / 1000.0;
            sb.append("Actual bitrate (kbps): ").append(bitrate).append("\n");

            // decode file
            cmd = test.decode;
            cmd = cmd.replaceAll("INFILE", encfile.getAbsolutePath());
            cmd = cmd.replaceAll("OUTFILE", decfile.getAbsolutePath());
            execCmd(cmd);

            // determine SSIM score
            cmd = SSIM;
            cmd = cmd.replaceAll("ORIGINAL", infile.getAbsolutePath());
            cmd = cmd.replaceAll("DECODED", decfile.getAbsolutePath());
            String lastline = execCmd(cmd);
            sb.append("SSIM output: ").append(lastline).append("\n\n");

            double score = Double.parseDouble(lastline.substring(lastline.indexOf(":") + 1, lastline.indexOf("(Y")));

            // write log
            LogEntry logentry = new LogEntry(test, encsize, bitrate, score, enctime);
            addToLog(test.logfile.getAbsolutePath(), logentry);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // cleanup
        encfile.delete();
        decfile.delete();
        // x264 creates a .mbtree file for the pass file. Clean this up as well.
        File mbtreefile = new File(passfile.getAbsolutePath() + ".mbtree");
        if (mbtreefile.exists()) {
            mbtreefile.delete();
        }
        passfile.delete();

        System.out.println(sb.toString());
    }

    public void runTestsThreaded(Queue<TestDescription> tests) throws Exception {
        logs.clear();

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < Runtime.getRuntime().availableProcessors(); ++i) {
            threads.add(new TestThread(tests));
        }

        for (Thread t : threads) {
            t.start();
        }

        for (Thread t : threads) {
            t.join();
        }

        // write logs
        LogWriter.writeEncoderLogs(logs);
        LogWriter.writeGlobalLog(logs);
    }

    public void createTests(TestDescription descr, Queue<TestDescription> tests) throws Exception {

        int kbits = descr.kbitstart;
        int kbitsinc = descr.kbitinc;

        double quant = descr.quantstart;
        double quantend = descr.quantend;
        double quantinc = descr.quantinc;

        if (quantinc <= 0) {
            while (kbits <= descr.kbitend) {
                TestDescription test = new TestDescription(descr);
                test.kbitstart = kbits;
                test.kbitend = kbits;
                test.kbitinc = descr.kbitinc;
                tests.add(test);
                kbits += kbitsinc;
            }
        } else {
            while (quant <= quantend) {
                TestDescription test = new TestDescription(descr);
                test.quantstart = quant;
                test.quantend = quantend;
                test.quantinc = quantinc;
                tests.add(test);
                quant += quantinc;
            }
        }
    }

    public static void main(String[] args) throws Exception {

        Testrunner r = new Testrunner();
        Queue<TestDescription> tests = new LinkedList<>();

        File xmlconfig;
        if (args.length == 0) {
            xmlconfig = new File("./src/main/xml/quant-irene.xml");
        } else {
            xmlconfig = new File(args[0]);
        }

        for (TestDescription desc : ConfigReader.readDescriptions(xmlconfig)) {
            r.createTests(desc, tests);
        }

        r.runTestsThreaded(tests);

    }

}
