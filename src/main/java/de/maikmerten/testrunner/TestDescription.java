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
	double quantstart = 0;
	double quantend = 0;
	double quantinc = 0;
	String suffix = ".bin";
  
    
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
		this.quantstart = descr.quantstart;
		this.quantend = descr.quantend;
		this.quantinc = descr.quantinc;
        this.decode = descr.decode;
        this.pass1 = descr.pass1;
        this.pass2 = descr.pass2;
		this.suffix = descr.suffix;
        this.logfile = descr.logfile;
        this.globallogfile = descr.globallogfile;
    }

}
