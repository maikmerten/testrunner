package de.maikmerten.testrunner;

import java.util.Comparator;

/**
 *
 * @author merten
 */
public class LogEntry {
    
    TestDescription test;
    double bitrate;
    long filesize;
    double ssimScore;
    long enctime;

    
    public LogEntry(TestDescription test, long filesize, double bitrate, double ssimScore, long enctime) {
        this.filesize = filesize;
        this.bitrate = bitrate;
        this.ssimScore = ssimScore;
        this.enctime = enctime;
        this.test = test;
    }
    
    public String getCsvString() {
        StringBuilder sb = new StringBuilder();
        sb.append(test.codecname).append(";").append(test.input).append(";").append(test.kbitstart).append(";");
        sb.append(filesize).append(";").append(bitrate).append(";").append(ssimScore).append(";");
        sb.append(enctime).append(";").append(test.pass1).append(";").append(test.pass2).append("\n");
        return sb.toString();
    }
    
    public static String getCsvHeader() {
        StringBuilder sb = new StringBuilder();
        sb.append("Codec").append(";").append("Input file").append(";").append("Bitrate target (kbps)").append(";");
        sb.append("Encoded file size").append(";").append("Actual bitrate (kbps)").append(";").append("SSIM score").append(";");
        sb.append("Encoding time (ms)").append(";").append("Pass 1 command").append(";").append("Pass 2 command").append("\n");
        return sb.toString();        
    }
    
    public static Comparator<LogEntry> getComparator() {
        return new Comparator<LogEntry>() {

            @Override
            public int compare(LogEntry t, LogEntry t1) {
                double diff = t.bitrate - t1.bitrate;
                if(diff < 0.0) return -1;
                if(diff > 0.0) return 1;
                return 0;
            }
        };
    }
    
    
}
