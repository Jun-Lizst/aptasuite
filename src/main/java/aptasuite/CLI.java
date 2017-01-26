/**
 * 
 */
package aptasuite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DateUtils;

import exceptions.InvalidConfigurationException;
import lib.aptamer.datastructures.Experiment;
import lib.aptamer.datastructures.SelectionCycle;
import lib.parser.aptaplex.AptaplexParser;
import lib.structure.capr.CapR;
import lib.structure.capr.CapRFactory;
import lib.structure.capr.CapROriginal;
import lib.structure.capr.InitLoops;
import utilities.AptaLogger;
import utilities.CLIOptions;
import utilities.Configuration;

/**
 * @author Jan Hoinka Implements the command line interface version of
 *         aptasuite.
 */
public class CLI {

	/**
	 * Instance of the current experiment
	 */
	Experiment experiment = null;

	/**
	 * The thread used to control the parser. Running the parser in this thread
	 * allows for nearly real-time estimates of the parsing progress.
	 */
	Thread parserThread = null;
	
	/**
	 * The thread used to control the parser. Running the parser in this thread
	 * allows for nearly real-time estimates of the parsing progress.
	 */
	Thread structureThread = null;	

	public CLI(CommandLine line) {

		// Make sure the parameter for the configuration file is present
		if(!line.hasOption("config")){
			throw new InvalidConfigurationException("No configuration file was specified. Please use the option --config /path/to/configuiration/file.cfg");
		}
		
		// Make sure the configuration file is valid
		Path cfp = Paths.get(line.getOptionValue("config"));

		if (Files.notExists(cfp)) {
				throw new InvalidConfigurationException("The configuration file could not be found at the specified path.");
		}
		
		// Read config file and set defaults
		Configuration.setConfiguration(line.getOptionValue("config"));
		
		AptaLogger.log(Level.INFO, Configuration.class, "Using the following parameters: " + "\n" +  Configuration.printParameters());
		
		// Make sure the project folder exists and create it if not
		Path projectPath = Paths.get(Configuration.getParameters().getString("Experiment.projectPath"));
		if (Files.notExists(projectPath)){
				AptaLogger.log(Level.INFO, this.getClass(), "The project path does not exist on the file system. Creating folder " + projectPath);
				try {
					Files.createDirectories(Paths.get(projectPath.toString()));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}

		// Case for AptaPLEX, create a database or overwrite an existing one
		if (line.hasOption("parse")){
			
			// clean up old data if required
			try {
				FileUtils.deleteDirectory(Paths.get(projectPath.toString(), "pooldata").toFile());
				FileUtils.deleteDirectory(Paths.get(projectPath.toString(), "cycledata").toFile());
				FileUtils.deleteDirectory(Paths.get(projectPath.toString(), "structuredata").toFile());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			// call data input logic
			createDatabase( line.getOptionValue("config") );
		}
		
		// Case for AptaTRACE
		if (line.hasOption("structures")){
			
			// clean up old data if required
			try {
				FileUtils.deleteDirectory(Paths.get(projectPath.toString(), "structuredata").toFile());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
						
			runStructurePrediction( line.getOptionValue("config") );

		}		
		
		// Case for AptaTRACE
		if (line.hasOption("trace")){

			runAptaTrace( line.getOptionValue("config") );

		}
		
		//TODO: Remove this for production
		System.out.println("Final Wait");
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	/**
	 * Implements the logic for calling APTAPlex and for creating a local database 
	 * using the sequencing data specified in the configuration file
	 * @param configFile
	 */
	private void createDatabase(String configFile) {
		
		AptaLogger.log(Level.INFO, this.getClass(), "Creating Database");
		
		// Initialize the experiment
		this.experiment = new Experiment(configFile, true);

		AptaLogger.log(Level.INFO, this.getClass(), "Initializing Experiment");
		AptaLogger.log(Level.INFO, this.getClass(), experiment.getSelectionCycleConfiguration());

		// Initialize the parser and run it in a thread
		AptaLogger.log(Level.INFO, this.getClass(), "Initializing parser " + Configuration.getParameters().getString("Parser.backend"));
		AptaplexParser parser = new AptaplexParser();

		parserThread = new Thread(parser);

		AptaLogger.log(Level.INFO, this.getClass(), "Starting Parser:");
		long tParserStart = System.currentTimeMillis();
		parserThread.start();

		// we need to add a shutdown hook for the parserThread in case the
		// user presses ctl-c
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					if (parserThread != null) {

						parserThread.interrupt();
						parserThread.join();

					}
				} catch (InterruptedException e) {
					AptaLogger.log(Level.SEVERE, this.getClass(), "User interrupt on parserThread");
				}
			}
		});

		// Update user about parsing progress
		String spacing = "%1$-23s %2$-23s %3$-23s %4$-23s %5$-23s %6$-23s %7$-23s %8$-23s";

		AptaLogger.log(Level.INFO, this.getClass(), "Parsing...");
		System.out.println(
				String.format(spacing, "Total Reads:", "Accepted Reads:", "Contig Assembly Fails:", "Invalid Alphabet:",
						"5' Primer Error:", "3' Primer Error:", "Invalid Cycle", "Total Primer Overlaps:"));
		System.out.flush();
		while (parserThread.isAlive() && !parserThread.isInterrupted()) {
			try {
				System.out.print(String.format(spacing + "\r", parser.getProgress().totalProcessedReads.get(),
						parser.getProgress().totalAcceptedReads.get(),
						parser.getProgress().totalContigAssemblyFails.get(),
						parser.getProgress().totalInvalidContigs.get(),
						parser.getProgress().totalUnmatchablePrimer5.get(),
						parser.getProgress().totalUnmatchablePrimer3.get(),
						parser.getProgress().totalInvalidCycle.get(), parser.getProgress().totalPrimerOverlaps.get()));
				// Once every second should suffice
				Thread.sleep(1000);
			} catch (InterruptedException ie) {
			}
		}
		// final update
		System.out.println(String.format(spacing + "\r", parser.getProgress().totalProcessedReads.get(),
				parser.getProgress().totalAcceptedReads.get(), parser.getProgress().totalContigAssemblyFails.get(),
				parser.getProgress().totalInvalidContigs.get(), parser.getProgress().totalUnmatchablePrimer5.get(),
				parser.getProgress().totalUnmatchablePrimer3.get(), parser.getProgress().totalInvalidCycle.get(),
				parser.getProgress().totalPrimerOverlaps.get()));

		// now that we have the data set any file backed implementations of the
		// pools and cycles to read only
		experiment.getAptamerPool().setReadOnly();
		for (SelectionCycle cycle : experiment.getAllSelectionCycles()) {
			if (cycle != null) {
				cycle.setReadOnly();
			}
		}

		AptaLogger.log(Level.INFO, this.getClass(), String.format("Parsing Completed in %s seconds.\n",
				((System.currentTimeMillis() - tParserStart) / 1000.0)));

		// TODO: print parsing statistics here
		AptaLogger.log(Level.INFO, this.getClass(), "Selection Cycle Statistics");
		for (SelectionCycle cycle : Configuration.getExperiment().getAllSelectionCycles()) {
			if (cycle != null) {
				AptaLogger.log(Level.INFO, this.getClass(), cycle.toString());
			}
		}

		// clean up
		parserThread = null;
		parser = null;

	}

	
	private void runStructurePrediction(String configFile){
		
		AptaLogger.log(Level.INFO, this.getClass(), "Starting Structure Predition");
		
		// Make sure we have prior data or load it from disk
		if (experiment == null) {
			AptaLogger.log(Level.INFO, this.getClass(), "Loading data from disk");
			this.experiment = new Experiment(configFile, false);
		}
		else{
			AptaLogger.log(Level.INFO, this.getClass(), "Using existing sequencing data");
		}
		
		// Create a new instance of the StructurePool
		experiment.instantiateStructurePool(true);
		
		// Start parallel processing of structure prediction
		CapRFactory caprf = new CapRFactory(experiment.getAptamerPool().iterator());
		
		structureThread = new Thread(caprf);

		AptaLogger.log(Level.INFO, this.getClass(), "Starting Structure Prediction using " + Configuration.getParameters().getInt("Performance.maxNumberOfCores") + " threads:");
		long tParserStart = System.currentTimeMillis();
		structureThread.start();

		// we need to add a shutdown hook for the CapRFactory in case the
		// user presses ctl-c
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					if (structureThread != null) {

						structureThread.interrupt();
						structureThread.join();

					}
				} catch (InterruptedException e) {
					AptaLogger.log(Level.SEVERE, this.getClass(), "User interrupt on structureTread");
				}
			}
		});

		AptaLogger.log(Level.INFO, this.getClass(), "Predicting...");

		long sps = 0;
		while (structureThread.isAlive() && !structureThread.isInterrupted()) {
			try {
				long current_progress = caprf.getProgress().longValue();
				long eta = (experiment.getAptamerPool().size()-current_progress)/(current_progress-sps+1);
				System.out.print(String.format("Completed: %s/%s (%s structures per second  ETA:%s)     " + "\r", current_progress, experiment.getAptamerPool().size(), current_progress-sps, String.format("%02d:%02d:%02d", eta / 3600, (eta % 3600) / 60, eta % 60)));
				sps = current_progress;
				
				// Once every second should suffice
				Thread.sleep(1000);
				
			} catch (InterruptedException ie) {
			}
		}
		// final update
		System.out.print(        String.format("Completed: %s/%s                                            ", caprf.getProgress(), experiment.getAptamerPool().size()));

		AptaLogger.log(Level.INFO, this.getClass(), String.format("Structure prediction completed in %s seconds.\n",
				((System.currentTimeMillis() - tParserStart) / 1000.0)));
		
	}
	
	
	/**
	 * Implements the logic for calling AptaTrace
	 */
	private void runAptaTrace(String configFile) {
		
		AptaLogger.log(Level.INFO, this.getClass(), "Starting AptaTRACE");
		
		// Make sure we have data prior or load it from disk
		if (experiment == null) {
			AptaLogger.log(Level.INFO, this.getClass(), "Loading data from disk");
			this.experiment = new Experiment(configFile, false);
		}
		else{
			AptaLogger.log(Level.INFO, this.getClass(), "Using existing data");
		}
		
		// Get the instance of the StructurePool
		if (experiment.getStructurePool() == null)
		{
			experiment.instantiateStructurePool(false);
		}	
		
		// TEMP print aptamer and counts
		long tParserStart = System.currentTimeMillis();
		int counter = 0;
		StringBuilder sb = new StringBuilder();
		for (Entry<byte[], Integer> aptamer : experiment.getAptamerPool().iterator()){
			counter++;
			sb.append(new String(aptamer.getKey()));
			sb.append("\t");
			for (SelectionCycle sc : experiment.getAllSelectionCycles()){
				sb.append(sc.getAptamerCardinality(aptamer.getValue()));
				sb.append("\t");
			}
			sb.append("\t");
//			System.out.println(sb.toString());
			sb.setLength(0);
		}
		AptaLogger.log(Level.INFO, this.getClass(), String.format("Iterated %s sequences in %s seconds.\n",
				counter, ((System.currentTimeMillis() - tParserStart) / 1000.0)));
	}
	
}
