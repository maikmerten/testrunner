package de.maikmerten.testrunner;

import java.io.File;

/**
 *
 * @author merten
 */
public class TestDescription {

    String codecname, pass1, pass2,decode;
    File input, logfile, globallogfile;
    int kbitstart = 128;
    int kbitend = 1024;
    int kbitinc = 128;
    double inputduration;
  
    
    public TestDescription() {
        super();
    }

    public TestDescription(TestDescription descr) {
        this();
        this.codecname = descr.codecname;
        this.input = descr.input;
        this.inputduration = descr.inputduration;
        this.kbitstart = descr.kbitstart;
        this.kbitend = descr.kbitend;
        this.kbitinc = descr.kbitinc;
        this.decode = descr.decode;
        this.pass1 = descr.pass1;
        this.pass2 = descr.pass2;
        this.logfile = descr.logfile;
        this.globallogfile = descr.globallogfile;
    }

}
