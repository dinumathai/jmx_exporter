package io.prometheus.jmx;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.instrument.Instrumentation;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.yaml.snakeyaml.Yaml;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.jmx.custom.hotspot.DefaultExports;

public class JavaAgent {

   static HTTPServer server;

   public static void agentmain(String agentArgument, Instrumentation instrumentation) throws Exception {
     premain(agentArgument, instrumentation);
   }

   public static void premain(String agentArgument, Instrumentation instrumentation) throws Exception {
     // Bind to all interfaces by default (this includes IPv6).
     String host = "0.0.0.0";

     // If we have IPv6 address in square brackets, extract it first and then
     // remove it from arguments to prevent confusion from too many colons.
     Integer indexOfClosingSquareBracket = agentArgument.indexOf("]:");
     if (indexOfClosingSquareBracket >= 0) {
       host = agentArgument.substring(0, indexOfClosingSquareBracket + 1);
       agentArgument = agentArgument.substring(indexOfClosingSquareBracket + 2);
     }

     String[] args = agentArgument.split(":");
     if (args.length < 2 || args.length > 3) {
       System.err.println("Usage: -javaagent:/path/to/JavaAgent.jar=[host:]<port>:<yaml configuration file>");
       System.exit(1);
     }

     int port;
     String file;
     InetSocketAddress socket;

     if (args.length == 3) {
       port = Integer.parseInt(args[1]);
       socket = new InetSocketAddress(args[0], port);
       file = args[2];
     } else {
       port = Integer.parseInt(args[0]);
       socket = new InetSocketAddress(host, port);
       file = args[1];
     }

     new BuildInfoCollector().register();
     new JmxCollector(new File(file)).register();
     
     Map<String, String> globalLabels = getJVMLabels(new File(file));
	 DefaultExports.initialize(globalLabels);
     
     server = new HTTPServer(socket, CollectorRegistry.defaultRegistry, true);
   }

   /**
    * Get the jvmLabels from the config yaml
    * @param in
    * @return
    * @throws FileNotFoundException 
    */
	private static Map<String, String> getJVMLabels(File in) throws FileNotFoundException {
		Map<String, String> globalLabels = new HashMap<String, String>();
		@SuppressWarnings("unchecked")
		Map<String, Object> yamlConfig = (Map<String, Object>) new Yaml().load(new FileReader(in));
		if (yamlConfig.containsKey("jvmLabels")) {
			@SuppressWarnings("unchecked")
			TreeMap<String, Object> labels = new TreeMap<String, Object>(
					(Map<String, Object>) yamlConfig.get("jvmLabels"));
			for (Map.Entry<String, Object> entry : (Set<Map.Entry<String, Object>>) labels.entrySet()) {
				globalLabels.put(entry.getKey(), (String) entry.getValue());
			}
		}
		return globalLabels;
	}
}
