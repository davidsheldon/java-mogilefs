
package com.guba.mogilefs;
import java.io.*;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.text.MessageFormat;

import org.apache.commons.cli.*;
import static org.apache.commons.cli.OptionBuilder.*;


/**
 * Mogtool -- command-line tool for interacting with Mogile
 * A poor-man's copy of the perl tool.
 *
 */
public class Mogtool {

	private static class MogToolOptions {
		String [] trackers;
		String storageClass;
		String domain;
		boolean verbose = false;
		private Options opts;
		private static final Pattern CONFIG_LINE = Pattern.compile("(\\w+)\\s*=\\s*(.+)");
		private boolean verify = false;

		MogToolOptions() {
			opts = new Options();
			opts.addOption(withArgName("trackers").withLongOpt("trackers").hasArg().withDescription("list of trackers").create("t"));
			opts.addOption(withArgName("class").withLongOpt("class").hasArg().withDescription("file class").create("c"));
			opts.addOption(withArgName("domain").withLongOpt("domain").hasArg().withDescription("domain to create them in").create("d"));
			opts.addOption(withArgName("configFile").withLongOpt("config").hasArg().withDescription("config file").create("conf"));
			opts.addOption(withDescription("should locate check the paths").withLongOpt("verify").create());


			opts.addOption("h", false, "Show help");

		}

		String[] parse(String[] argv) {
			CommandLineParser parser = new PosixParser();

			boolean fail = false;

			CommandLine line = null;
			try {
				line = parser.parse(opts, argv);
			} catch (ParseException e) {

				fail = true;
			}

			if (fail || line.hasOption("h") || line.getArgList().isEmpty()) {
				showUsage();
				System.exit(1);
			}

			final Map<String, String> configMap = readConfigfiles(getConfigFiles(line));

			setDomain(configMap.get("domain"));
			setTrackers(configMap.get("trackers"));
			setVerify(configMap.get("verify"));
      setStorageClass(configMap.get("class"));

			setDomain(line.getOptionValue("d"));
			setTrackers(line.getOptionValue("t"));
      setStorageClass(line.getOptionValue("c"));


			if (domain == null || trackers == null) {
				showUsage();
				System.err.println("You need to specify both domain and trackers.");
				System.exit(1);
			}

			return line.getArgs();
		}

		private void setVerify(String ver) {
			if (ver != null) {
				verify = true;
			}
		}

		private void setDomain(String val) {
			if (val != null) {
				domain = val;
			}
		}

		private void setTrackers(String val) {
			if (val != null) {
				trackers = val.split("\\s*,\\s*");
			}
		}

    public void setStorageClass(final String val) {
      if (val != null) {
        this.storageClass = val;
      }
    }

    private List<String> getConfigFiles(CommandLine line) {
			List<String> ret = new LinkedList<String>();
			ret.add(line.getOptionValue("conf"));
			ret.add(System.getenv("HOME") + "/.mogtool");
			ret.add("/etc/mogilefs/mogtool.conf");

			return ret;
		}

		private Map<String, String> readConfigfiles(List<String> files) {
			Map<String, String> ret = new HashMap<String, String>();

			for (String file : files) {
				if (file != null) {
					parseFile(ret, file);
				}
			}

			return ret;
		}

		private void parseFile(Map<String, String> ret, String file) {
			try {
				BufferedReader r = new BufferedReader(new FileReader(file));

				String line;
				while((line = r.readLine()) != null)  {
					final Matcher matcher = CONFIG_LINE.matcher(line.replaceAll("#.*", ""));
					if (matcher.matches()) {
						String key = matcher.group(1);
						String value = matcher.group(2);
						if (! ret.containsKey(key)) {
							ret.put(key, value);
						}
					}
				}

			} catch (FileNotFoundException e) {
				System.err.println("Unable to open " + file);
			} catch (IOException e) {
				System.err.println("Unable to read " + file);
			}
		}

		public void showUsage() {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("mogTool <command> ...", "", opts, "\nCommand is one of:\n" +
				"- inject thefile.tgz thefilekey\n" +
				"- extract thefilekey thenewfile.tgz\n" +
				"- delete thekey\n" +
				"- locate thekey\n" +
				"- listkey - TODO\n" +
				"Just like in the perl mogtool but without bigfiles.");

			

		}

		protected MogileFS createMogileFS() throws NoTrackersException, BadHostFormatException {
			return new PooledMogileFSImpl(domain, trackers,
				5, 2, 30000);
	}

		public String getStorageClass() {
			return storageClass;
		}

		public boolean getVerify() {
			return verify;
		}
	}


	/**
	 * @param argv The command line.
	 */
	public static void main(String[] argv) {
		MogToolOptions options = new MogToolOptions();

		String[] args = options.parse(argv);


		final String command = args[0];
		if ("inject".equalsIgnoreCase(command) || "i".equalsIgnoreCase(command)) {
			if (args.length < 3) {
				System.err.println("mogtool inject <filename> <key>");
				return;
			}
			try {
				inject(args[2], options.getStorageClass(), args[1], options.createMogileFS());
			} catch (MogileException e) {
				System.err.println("Error trying to inject file: " + e);
				e.printStackTrace();
			}
		}
		else if ("extract".equalsIgnoreCase(command) || "x".equalsIgnoreCase(command)) {
			if (args.length < 3) {
				System.err.println("mogtool extract <key> <filename>");
				return;
			}
			try {
				extract(args[1], args[2], options.createMogileFS());
			} catch (Exception e) {
				System.err.println("Error trying to inject file: " + e);
				e.printStackTrace();
			}

		}
		else if ("delete".equalsIgnoreCase(command) || "rm".equalsIgnoreCase(command)) {
			if (args.length < 2) {
				System.err.println("mogtool delete <key>");
				return;
			}
			try {
				MogileFS mogfs = options.createMogileFS();
				mogfs.delete(args[1]);
			} catch (Exception e) {
				System.err.println("Error deleting file: " + e);
				e.printStackTrace();
			}
		}
		else if ("locate".equalsIgnoreCase(command) || "lo".equalsIgnoreCase(command)) {
			if (args.length < 2) {
				System.err.println("mogtool locate <key>");
				return;
			}
			try {
				MogileFS mogfs = options.createMogileFS();
				int count = 0;
				for (String path : mogfs.getPaths(args[1], options.getVerify())) {
					if (path != null) {
						System.out.println(path);
						count++;
					}
					System.out.println(MessageFormat.format("#{0} paths found", count));
				}
			} catch (Exception e) {
				System.err.println("Error locating file: " + e);
				e.printStackTrace();
			}

		}
		else if ("list".equalsIgnoreCase(command) || "ls".equalsIgnoreCase(command)) {
			System.err.println("List not supported - we don't really do bigfiles.");
		}
		else if ("listkey".equalsIgnoreCase(command)|| "lsk".equalsIgnoreCase(command)) {
			System.err.println("TODO: listkey not supported yet.");
		}

		else {
			System.err.println("Unknown command: '" + command + "'");
			options.showUsage();
		}
	}

	private static void extract(String key, String file, MogileFS mogileFS) throws IOException, StorageCommunicationException, TrackerCommunicationException, NoTrackersException {
		mogileFS.getFile(key, new File(file));
	}


	public static void inject(String key, String storageClass, String filename, final MogileFS mogileFS) throws MogileException {

		File file = new File(filename);
		System.out.println("storing " + file + " as " + key + " to " + mogileFS);
		mogileFS.storeFile(key, storageClass, file);
	}


}
