package io.prometheus.jmx.custom.hotspot;

import io.prometheus.client.Collector;
import io.prometheus.client.CounterMetricFamily;
import io.prometheus.client.GaugeMetricFamily;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Exports the standard exports common across all prometheus clients.
 * <p>
 * This includes stats like CPU time spent and memory usage.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 *   new StandardExports().register();
 * }
 * </pre>
 */
public class StandardExports extends Collector {
  private static final Logger LOGGER = Logger.getLogger(StandardExports.class.getName());

  private final StatusReader statusReader;
  private final OperatingSystemMXBean osBean;
  private final RuntimeMXBean runtimeBean;
  private final boolean linux;
  
  private final List<String> globalLabelNames;
  private final List<String> globalLabelValues;

  public StandardExports(Map<String, String> globalLabelsMap) {
    this(new StatusReader(),
         ManagementFactory.getOperatingSystemMXBean(),
         ManagementFactory.getRuntimeMXBean(), globalLabelsMap);
  }

  StandardExports(StatusReader statusReader, OperatingSystemMXBean osBean, RuntimeMXBean runtimeBean, Map<String, String> globalLabelsMap) {
      this.statusReader = statusReader;
      this.osBean = osBean;
      this.runtimeBean = runtimeBean;
      this.linux = (osBean.getName().indexOf("Linux") == 0);
      if(globalLabelsMap.size() == 0){
          this.globalLabelNames = new ArrayList<String>();
          this.globalLabelValues = new ArrayList<String>();
      }else{
        this.globalLabelNames = new ArrayList<String>(globalLabelsMap.keySet());
        this.globalLabelValues = new ArrayList<String>(globalLabelsMap.values());
    }
  }

  private final static double KB = 1024;

  @Override
  public List<MetricFamilySamples> collect() {
    List<MetricFamilySamples> mfs = new ArrayList<MetricFamilySamples>();

    try {
      // There exist at least 2 similar but unrelated UnixOperatingSystemMXBean interfaces, in
      // com.sun.management and com.ibm.lang.management. Hence use reflection and recursively go
      // through implemented interfaces until the method can be made accessible and invoked.
      Long processCpuTime = callLongGetter("getProcessCpuTime", osBean);
      CounterMetricFamily processCpuSecTot = new CounterMetricFamily("process_cpu_seconds_total", "Total user and system CPU time spent in seconds.", this.globalLabelNames);
      processCpuSecTot.addMetric(this.globalLabelValues, processCpuTime / NANOSECONDS_PER_SECOND);
      mfs.add(processCpuSecTot);
    }catch (Exception e) {
      LOGGER.log(Level.FINE,"Could not access process cpu time", e);
    }
    
    GaugeMetricFamily processStrtTimSec = new GaugeMetricFamily("process_start_time_seconds", "Start time of the process since unix epoch in seconds.", this.globalLabelNames);
    processStrtTimSec.addMetric(this.globalLabelValues, runtimeBean.getStartTime() / MILLISECONDS_PER_SECOND);
    mfs.add(processStrtTimSec);

    // There exist at least 2 similar but unrelated UnixOperatingSystemMXBean interfaces, in
    // com.sun.management and com.ibm.lang.management. Hence use reflection and recursively go
    // through implemented interfaces until the method can be made accessible and invoked.
    try {
      Long openFdCount = callLongGetter("getOpenFileDescriptorCount", osBean);
      GaugeMetricFamily processOpenFDs = new GaugeMetricFamily("process_open_fds", "Number of open file descriptors.", this.globalLabelNames);
      processOpenFDs.addMetric(this.globalLabelValues, openFdCount);
      mfs.add(processOpenFDs);
      
      Long maxFdCount = callLongGetter("getMaxFileDescriptorCount", osBean);
      GaugeMetricFamily processMaxFDs = new GaugeMetricFamily("process_max_fds", "Maximum number of open file descriptors.", this.globalLabelNames);
      processMaxFDs.addMetric(this.globalLabelValues,  maxFdCount);
      mfs.add(processMaxFDs);
      
    } catch (Exception e) {
      // Ignore, expected on non-Unix OSs.
    }

    // There's no standard Java or POSIX way to get memory stats,
    // so add support for just Linux for now.
    if (linux) {
      try {
        collectMemoryMetricsLinux(mfs);
      } catch (Exception e) {
        // If the format changes, log a warning and return what we can.
        LOGGER.warning(e.toString());
      }
    }
    return mfs;
  }

  static Long callLongGetter(String getterName, Object obj)
      throws NoSuchMethodException, InvocationTargetException {
    return callLongGetter(obj.getClass().getMethod(getterName), obj);
  }

  /**
   * Attempts to call a method either directly or via one of the implemented interfaces.
   * <p>
   * A Method object refers to a specific method declared in a specific class. The first invocation
   * might happen with method == SomeConcreteClass.publicLongGetter() and will fail if
   * SomeConcreteClass is not public. We then recurse over all interfaces implemented by
   * SomeConcreteClass (or extended by those interfaces and so on) until we eventually invoke
   * callMethod() with method == SomePublicInterface.publicLongGetter(), which will then succeed.
   * <p>
   * There is a built-in assumption that the method will never return null (or, equivalently, that
   * it returns the primitive data type, i.e. {@code long} rather than {@code Long}). If this
   * assumption doesn't hold, the method might be called repeatedly and the returned value will be
   * the one produced by the last call.
   */
  static Long callLongGetter(Method method, Object obj) throws InvocationTargetException  {
    try {
      return (Long) method.invoke(obj);
    } catch (IllegalAccessException e) {
      // Expected, the declaring class or interface might not be public.
    }

    // Iterate over all implemented/extended interfaces and attempt invoking the method with the
    // same name and parameters on each.
    for (Class<?> clazz : method.getDeclaringClass().getInterfaces()) {
      try {
        Method interfaceMethod = clazz.getMethod(method.getName(), method.getParameterTypes());
        Long result = callLongGetter(interfaceMethod, obj);
        if (result != null) {
          return result;
        }
      } catch (NoSuchMethodException e) {
        // Expected, class might implement multiple, unrelated interfaces.
      }
    }

    return null;
  }

  void collectMemoryMetricsLinux(List<MetricFamilySamples> mfs) {
    // statm/stat report in pages, and it's non-trivial to get pagesize from Java
    // so we parse status instead.
    BufferedReader br = null;
    try {
      br = statusReader.procSelfStatusReader();
      String line;
      while ((line = br.readLine()) != null) {
        if (line.startsWith("VmSize:")) {
          GaugeMetricFamily processVirMemByt = new GaugeMetricFamily("process_virtual_memory_bytes", "Virtual memory size in bytes.", this.globalLabelNames);
          processVirMemByt.addMetric(this.globalLabelValues, Float.parseFloat(line.split("\\s+")[1]) * KB);
          mfs.add(processVirMemByt);
        } else if (line.startsWith("VmRSS:")) {
          GaugeMetricFamily processResMemByt = new GaugeMetricFamily("process_resident_memory_bytes", "Resident memory size in bytes.", this.globalLabelNames);
          processResMemByt.addMetric(this.globalLabelValues, Float.parseFloat(line.split("\\s+")[1]) * KB);
          mfs.add(processResMemByt);
        }
      }
    } catch (IOException e) {
      LOGGER.fine(e.toString());
    } finally {
      if (br != null) {
        try {
          br.close();
        } catch (IOException e) {
          LOGGER.fine(e.toString());
        }
      }
    }
  }

  static class StatusReader {
    BufferedReader procSelfStatusReader() throws FileNotFoundException {
      return new BufferedReader(new FileReader("/proc/self/status"));
    }
  }
}
