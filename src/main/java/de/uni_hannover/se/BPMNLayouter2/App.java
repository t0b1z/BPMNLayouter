package de.uni_hannover.se.BPMNLayouter2;

import java.io.File;
import java.util.HashMap;

import org.activiti.bpmn.model.BpmnModel;
import org.apache.commons.io.FileUtils;
import org.jdom2.Element;

import de.uni_hannover.se.BPMNLayouter2.layouter.SimpleGridLayouter;
import de.uni_hannover.se.BPMNLayouter2.util.Util;

public class App 
{
	
    private static boolean move = false;

	public static void main( String[] args ) throws Exception
    {
    	String filename = "";
    	//filename = "real_diagrams/ApprovalProcess"; //-- Works!
    	//filename = "real_diagrams/ExecuteNomineeBookings"; // -- Works!
    	//String filename = "real_diagrams/Nachlieferung-TC8-2Nachlieferungen"; -- Works!
    	//filename = "real_diagrams/P8CreditorTransfer(fromSIS)-Level2"; // -- Works!
    	//filename = "real_diagrams/SISZuordnungvonDispo-Pool-Dokumenten"; // -- Works! 
    	//filename = "real_diagrams/P9"; // -- Works!
    	//filename = "real_diagrams/P15-GBDBS21-Grundbuch-Mitteilungen_-Anzeigen"; // -- Works!
    	//filename = "real_diagrams/P14-GBDBS21-GesuchZustimmung_Bewilligung"; // -- Works!
    	//filename = "real_diagrams/TaxOfficeIntegration"; // -- Works!

    	//filename = "real_diagrams/GBDBS21-NachlieferungExtensions"; // -- API ERROR
    	//filename = "real_diagrams/P10OwnerExchange"; //  -- API ERROR
    	
    	filename = "bigsub";

    	for(String arg : args)
		{
    		if(arg.equals("-move"))
    			move  = true;
		}
    	    	
    	if(args.length > 0)
    	{
        	layoutFiles(args);
    	}
    	else
    		layoutFile(filename);
    }

	private static void layoutFiles(String[] files)
			throws Exception {
		for(String file : files)
		{
			layoutFile(file);
		}
	}
    
    static void layoutFile(String filename) throws Exception
    {
    	String filePath = "res/" + filename + ".bpmn";
    	File file = new File(filePath);
    	File copy = new File(filePath + "copy");
    	FileUtils.copyFile(file, copy);
    	
    	HashMap<String, Element> extensionMap = Util.removeAndGetElementsFromXML(filePath + "copy", "extensionElements");
    	//HashMap<String, Element> sedMap = Util.removeAndGetElementsFromXML(filePath + "copy", "signalEventDefinition");

    	BpmnModel model = Util.readBPMFile(copy);
		
    	SimpleGridLayouter layouter = new SimpleGridLayouter(model);
    	try {
    		layouter.layoutModelToGrid(move);
    	}catch(Exception e)
    	{
        	layouter = new SimpleGridLayouter(model);
    		layouter.layoutModelToGrid(false);
    	}
    	layouter.applyGridToModel();
		
    	String name = "target/" + filename + "_layout.bpmn";
    	Util.writeModel(model, name);
    	Util.addXMLElementsBackToFile(extensionMap, name);
    	//Util.addXMLElementsBackToFile(sedMap, name);
    	
    	copy.delete();
    }
}
