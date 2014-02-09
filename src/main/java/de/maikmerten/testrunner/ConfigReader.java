package de.maikmerten.testrunner;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author merten
 */
public class ConfigReader {

    public static Collection<TestDescription> readDescriptions(File xmlfile) throws Exception {
        List<TestDescription> descriptions = new ArrayList<>();

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlfile);
        doc.getDocumentElement().normalize();
        
               
        Node test = doc.getElementsByTagName("test").item(0);
        int bitratemin = Integer.parseInt(test.getAttributes().getNamedItem("bitratemin").getTextContent());
        int bitratemax = Integer.parseInt(test.getAttributes().getNamedItem("bitratemax").getTextContent());
        int bitrateincr = Integer.parseInt(test.getAttributes().getNamedItem("bitrateincr").getTextContent());
        
        File globallogfile = null;
        for(int i = 0; i < test.getChildNodes().getLength(); ++i) {
            Node child = test.getChildNodes().item(i);
            if(child.getNodeName().equals("logfile")) {
                globallogfile = new File(child.getTextContent().trim());
            }
        }
        
        System.out.println("Bitrate from " + bitratemin + " kbps to " + bitratemax + " kbps in steps of " + bitrateincr + " kbps");
        
        Node input = doc.getElementsByTagName("input").item(0);
        File inputfile = new File(input.getTextContent());
        double duration = Double.parseDouble(input.getAttributes().getNamedItem("duration").getTextContent());
        System.out.println(inputfile.getAbsolutePath() + " is " + duration + " seconds long");
        System.out.println("Global log to " + globallogfile);
        System.out.println();

        
        NodeList encoders = doc.getElementsByTagName("encoder");
        for(int i = 0; i < encoders.getLength(); ++i) {
            Node encoder = encoders.item(i);
            
            Node skip = encoder.getAttributes().getNamedItem("skip");
            if(skip != null && (skip.getTextContent().equals("1") || skip.getTextContent().equals("true"))) {
                continue;
            }
            
            String codecname = null;
            Node name = encoder.getAttributes().getNamedItem("name");
            if(name != null) {
                codecname = name.getTextContent().trim();
            }
            
            String pass1 = null;
            String pass2 = null;
            String decode = null;
            File logfile = null;
            
            NodeList childs = encoder.getChildNodes();
            for(int j = 0; j < childs.getLength(); ++j) {
                Node child = childs.item(j);
                switch (child.getNodeName()) {
                    case "pass1":
                        pass1 = child.getTextContent().trim();
                        break;
                    case "pass2":
                        pass2 = child.getTextContent().trim();
                        break;
                    case "decode":
                        decode = child.getTextContent().trim();
                        break;
                    case "logfile":
                        logfile = new File(child.getTextContent());
                        break;
                }
            }
            
            System.out.println(codecname);
            System.out.println(pass1);
            System.out.println(pass2);
            System.out.println(decode);
            System.out.println(logfile);
            System.out.println();
            
            TestDescription descr = new TestDescription();
            descr.codecname = codecname;
            descr.input = inputfile;
            descr.inputduration = duration;
            descr.kbitstart = bitratemin;
            descr.kbitend = bitratemax;
            descr.kbitinc = bitrateincr;
            descr.pass1 = pass1;
            descr.pass2 = pass2;
            descr.decode = decode;
            descr.logfile = logfile;
            descr.globallogfile = globallogfile;
            descriptions.add(descr);
        }

        return descriptions;
    }
    
    public static void main(String[] args) throws Exception {
        
        File xmlfile = new File("irene.xml");
        ConfigReader.readDescriptions(xmlfile);
    }
    
}
