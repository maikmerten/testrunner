package de.maikmerten.testrunner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYSplineRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

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
            while(true) {
                try {
                    synchronized(queue) {
                        test = queue.poll();
                    }
                    if(test == null) break;
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
        Process p = Runtime.getRuntime().exec(cmd);
        BufferedReader error = new BufferedReader(new InputStreamReader(p.getErrorStream()));
        BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream()));

        WaitThread wt = new WaitThread(p);
        wt.start();

        String lastline = null;

        // read stderr and stdout
        while (wt.running) {
            Thread.sleep(10);
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
        if (out.ready()) {
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
    
    private void writeGlobalLog() throws Exception {
        List<LogEntry> allLogs = new ArrayList<>();
        for(String key : logs.keySet()) {
            allLogs.addAll(logs.get(key));
        }
        Collections.sort(allLogs, LogEntry.getComparator());
        
        Set<File> globallogfiles = new HashSet();
        for(LogEntry entry : allLogs) {
            globallogfiles.add(entry.test.globallogfile);
        }
        
        for(File logfile : globallogfiles) {
            if(logfile.exists()) {
                logfile.delete();
            }
            
            // assemble all entries for this log file
            List<LogEntry> entries = new ArrayList<>();
            Set<String> codecset = new HashSet<>();
            for(LogEntry e : allLogs) {
                if(e.test.globallogfile.equals(logfile)) {
                    entries.add(e);
                    codecset.add(e.test.codecname);
                }
            }
            List<String> codecs = new ArrayList<>(codecset);
            Collections.sort(codecs);
            
            StringBuilder sb = new StringBuilder();
            
            // build header
            sb.append("Bitrate (kbps);");
            for(String codec : codecs) {
                sb.append(codec).append(";");
            }
            sb.append("\n");
            
            // write entries
            for(LogEntry entry : entries) {
                sb.append(entry.bitrate);
                int idx = codecs.indexOf(entry.test.codecname);
                // push entry to correct column
                for(int i = 0; i < idx; ++i) {
                    sb.append(";");
                }
                sb.append(";").append(entry.ssimScore);
                int othercols = codecs.size() - 1 - idx;
                for(int i = 0; i < othercols; ++i) {
                    sb.append(";");
                }
                sb.append("\n");
            }
            
            FileOutputStream fos = new FileOutputStream(logfile);
            fos.write(sb.toString().getBytes());
            fos.close();

            // generate chart with JFreeChart
            String inputname = "";
            double maxssim = 0;
            double minssim = 999999;
            int tickunit = 64;
            File chartfile = new File(logfile.getAbsolutePath() + ".png");
            if(chartfile.exists()) {
                chartfile.delete();
            }
            
            XYSeriesCollection collection = new XYSeriesCollection();
            Map<String, XYSeries> seriesmap = new HashMap<>();
            for(LogEntry entry : entries) {
                String codecname = entry.test.codecname;
                XYSeries series = seriesmap.get(codecname);
                if(series == null) {
                    series = new XYSeries(codecname);
                    seriesmap.put(codecname, series);
                    collection.addSeries(series);
                }
                series.add(entry.bitrate, entry.ssimScore);
                
                if(entry.ssimScore < minssim) {
                    minssim = entry.ssimScore;
                }
                if(entry.ssimScore > maxssim) {
                    maxssim = entry.ssimScore;
                }
                tickunit = entry.test.kbitinc;
                inputname = entry.test.input.getName();
            }
            
            final JFreeChart chart = ChartFactory.createXYLineChart(
                    inputname,
                    "Bitrate (kbps)",
                    "SSIM",
                    collection,
                    PlotOrientation.VERTICAL,
                    true,
                    true,
                    false
            );
            // set some chart render options and save to PNG
            chart.getXYPlot().setRenderer(new XYSplineRenderer());
            chart.getXYPlot().getRangeAxis().setRange(minssim - 0.5, maxssim + 0.5);
            NumberAxis xAxis = (NumberAxis) chart.getXYPlot().getDomainAxis();
            xAxis.setTickUnit(new NumberTickUnit(tickunit));
            XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) chart.getXYPlot().getRenderer();
            renderer.setBaseShapesVisible(true);
            ChartUtilities.saveChartAsPNG(chartfile, chart, 800, 600);
        }
    }
    

    private void runTest(TestDescription test) throws Exception {
        int kbits = test.kbitstart;
        int bits = kbits * 1000;
        StringBuilder sb = new StringBuilder();

        File infile = test.input;
        if (!infile.exists()) {
            throw new RuntimeException("Cannot find " + infile.getAbsolutePath());
        }

        System.out.println("Starting test of " + test.codecname  +" with " + kbits + " kbit/s");
        sb.append("\nResults for ").append(test.codecname).append(" at ").append(kbits).append(" kbps:\n");

        File encfile = File.createTempFile("encoding", ".bin");
        File decfile = File.createTempFile("decoded", ".y4m");
        File passfile = File.createTempFile("pass", ".log");

        Date start = new Date();
        
        // Encode input file
        String cmd = test.pass1;
        cmd = cmd.replaceAll("INFILE", infile.getAbsolutePath());
        cmd = cmd.replaceAll("OUTFILE", encfile.getAbsolutePath());
        cmd = cmd.replaceAll("PASSFILE", passfile.getAbsolutePath());
        cmd = cmd.replaceAll("KBITS", "" + kbits);
        cmd = cmd.replaceAll("BITS", "" + bits);
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

        // cleanup
        encfile.delete();
        decfile.delete();
        passfile.delete();
        
        System.out.println(sb.toString());
    }
    
    public void runTestsThreaded(Queue<TestDescription> tests) throws Exception {
        logs.clear();
        
        List<Thread> threads = new ArrayList<>();
        for(int i = 0; i < Runtime.getRuntime().availableProcessors(); ++i) {
            threads.add(new TestThread(tests));
        }
        
        for(Thread t : threads) {
            t.start();
        }
        
        for(Thread t : threads) {
            t.join();
        }
        
        // write logs
        for(String logfile : logs.keySet()) {
            List<LogEntry> log = logs.get(logfile);
            Collections.sort(log, LogEntry.getComparator());
            File f = new File(logfile);
            if(f.exists()) {
                f.delete();
            }
            try (FileOutputStream logstream = new FileOutputStream(f)) {
                logstream.write(LogEntry.getCsvHeader().getBytes());
                for(LogEntry entry : log) {
                    logstream.write(entry.getCsvString().getBytes());
                }
            }
        }
        writeGlobalLog();
    }
    

    public void createTests(TestDescription descr, Queue<TestDescription> tests) throws Exception {

        int kbits = descr.kbitstart;
        int kbitsinc = descr.kbitinc;

        while (kbits <= descr.kbitend) {
            TestDescription test = new TestDescription(descr);
            test.kbitstart = kbits;
            test.kbitend = kbits;
            test.kbitinc = descr.kbitinc;
            tests.add(test);
            kbits += kbitsinc;
        }
    }

    public static void main(String[] args) throws Exception {

        Testrunner r = new Testrunner();
        Queue<TestDescription> tests = new LinkedList<>();
       
       
        File xmlconfig;
        if(args.length == 0) {
            xmlconfig = new File("./src/main/xml/irene.xml");
        } else {
            xmlconfig = new File(args[0]);
        }
        
        for(TestDescription desc :ConfigReader.readDescriptions(xmlconfig)) {
            r.createTests(desc, tests);
        }
        
        r.runTestsThreaded(tests);

    }

}
