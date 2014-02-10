package de.maikmerten.testrunner;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYSplineRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 *
 * @author merten
 */
public class LogWriter {

    public static void writeEncoderLogs(Map<String, List<LogEntry>> logs) throws Exception {
        for (String logfile : logs.keySet()) {
            List<LogEntry> log = logs.get(logfile);
            Collections.sort(log, LogEntry.getComparator());
            File f = new File(logfile);
            if (f.exists()) {
                f.delete();
            }
            try (FileOutputStream logstream = new FileOutputStream(f)) {
                logstream.write(LogEntry.getCsvHeader().getBytes());
                for (LogEntry entry : log) {
                    logstream.write(entry.getCsvString().getBytes());
                }
            }
        }
    }

    public static void writeGlobalLog(Map<String, List<LogEntry>> logs) throws Exception {
        List<LogEntry> allLogs = new ArrayList<>();
        for (String key : logs.keySet()) {
            allLogs.addAll(logs.get(key));
        }
        Collections.sort(allLogs, LogEntry.getComparator());

        Set<File> globallogfiles = new HashSet();
        for (LogEntry entry : allLogs) {
            globallogfiles.add(entry.test.globallogfile);
        }

        for (File logfile : globallogfiles) {
            if (logfile.exists()) {
                logfile.delete();
            }

            // assemble all entries for this log file
            List<LogEntry> entries = new ArrayList<>();
            Set<String> codecset = new HashSet<>();
            for (LogEntry e : allLogs) {
                if (e.test.globallogfile.equals(logfile)) {
                    entries.add(e);
                    codecset.add(e.test.codecname);
                }
            }
            List<String> codecs = new ArrayList<>(codecset);
            Collections.sort(codecs);

            StringBuilder sb = new StringBuilder();

            // build header
            sb.append("Bitrate (kbps);");
            for (String codec : codecs) {
                sb.append(codec).append(";");
            }
            sb.append("\n");

            // write entries
            for (LogEntry entry : entries) {
                sb.append(entry.bitrate);
                int idx = codecs.indexOf(entry.test.codecname);
                // push entry to correct column
                for (int i = 0; i < idx; ++i) {
                    sb.append(";");
                }
                sb.append(";").append(entry.ssimScore);
                int othercols = codecs.size() - 1 - idx;
                for (int i = 0; i < othercols; ++i) {
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

            XYSeriesCollection collection = new XYSeriesCollection();
            Map<String, XYSeries> seriesmap = new HashMap<>();
            for (LogEntry entry : entries) {
                String codecname = entry.test.codecname;
                XYSeries series = seriesmap.get(codecname);
                if (series == null) {
                    series = new XYSeries(codecname);
                    seriesmap.put(codecname, series);
                    collection.addSeries(series);
                }
                series.add(entry.bitrate, entry.ssimScore);
                
                if (entry.ssimScore < minssim) {
                    minssim = entry.ssimScore;
                }
                if (entry.ssimScore > maxssim) {
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
            XYPlot plot = chart.getXYPlot();
            plot.setBackgroundPaint(Color.BLACK);
            plot.setRenderer(new XYSplineRenderer());
            NumberAxis ssimAxis = (NumberAxis) plot.getRangeAxis();
            ssimAxis.setRange(minssim - 0.25, maxssim + 0.25);
            NumberAxis bitrateAxis = (NumberAxis) plot.getDomainAxis();
            bitrateAxis.setTickUnit(new NumberTickUnit(tickunit));
            XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
            renderer.setBaseShapesVisible(true);
            ChartUtilities.saveChartAsPNG(new File(logfile.getAbsolutePath() + ".linear.png"), chart, 800, 600);
            
            // log x axis
            LogarithmicAxis bitrateLogAxis = new LogarithmicAxis("Bitrate (kbps)");
            bitrateLogAxis.setTickUnit(new NumberTickUnit(tickunit));
            plot.setDomainAxis(bitrateLogAxis);
            ChartUtilities.saveChartAsPNG(new File(logfile.getAbsolutePath() + ".logarithmic.png"), chart, 800, 600);
            
            // swap x and y
            for(XYSeries series : seriesmap.values()) {
                List<XYDataItem> swappeditems = new ArrayList<>(series.getItemCount());
                for(int i = 0; i < series.getItemCount(); ++i) {
                    XYDataItem item = series.getDataItem(i);
                    swappeditems.add(new XYDataItem(item.getY(), item.getX()));
                }
                series.clear();
                for(XYDataItem item : swappeditems) {
                    series.add(item);
                }
            }
            plot.setDomainAxis(ssimAxis);
            bitrateAxis.setMinorTickCount(4);
            bitrateAxis.setMinorTickMarksVisible(true);
            plot.setRangeMinorGridlinesVisible(true);
            plot.setRangeMinorGridlinePaint(Color.DARK_GRAY);
            plot.setRangeAxis(bitrateAxis);
            ChartUtilities.saveChartAsPNG(new File(logfile.getAbsolutePath() + ".ratebyssim.png"), chart, 800, 600);
            
        }
    }

}
