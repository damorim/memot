package purity;

import java.io.IOException;
import java.lang.instrument.Instrumentation;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * This is the instrumentation agent class that must be passed in the command-line using the command below.
 * 
 * -javaagent:jarpath[=options]
 */
public class InstrumentationAgent {

  /**
   * This method is the entry point of the java agent.
   */
  public static void premain (String args, Instrumentation inst) throws IOException {
    if (args!=null) {
      try {
        //create the command line parser
        CommandLineParser parser = new DefaultParser();
        // create the Options object
        Options options = new Options();
        Option pname = Option.builder("pname").longOpt("pname").required().type(String.class).desc("package to monitor").hasArgs().build();
        options.addOption(pname);
        CommandLine cmd =  parser.parse(options, new String[]{args});
        Config.PACKAGE = cmd.getOptionValue("pname");
      } catch(ParseException pex) {
        pex.printStackTrace();
        throw new RuntimeException("Could not parse options");
      }
    }
    inst.addTransformer(new ClassInstrumenter());
  }

}