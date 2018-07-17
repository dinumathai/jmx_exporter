package io.prometheus.jmx.custom.hotspot;

import io.prometheus.client.Collector;
import io.prometheus.client.CounterMetricFamily;
import io.prometheus.client.GaugeMetricFamily;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exports metrics about JVM thread areas.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 *   new ThreadExports().register();
 * }
 * </pre>
 * Example metrics being exported:
 * <pre>
 *   jvm_threads_current{} 300
 *   jvm_threads_daemon{} 200
 *   jvm_threads_peak{} 410
 *   jvm_threads_started_total{} 1200
 * </pre>
 */
public class ThreadExports extends Collector {
  private final ThreadMXBean threadBean;
  private final List<String> globalLabelNames;
  private final List<String> globalLabelValues;

  public ThreadExports(Map<String, String> globalLabelsMap) {
    this(ManagementFactory.getThreadMXBean(), globalLabelsMap);
  }

  public ThreadExports(ThreadMXBean threadBean, Map<String, String> globalLabelsMap) {
    this.threadBean = threadBean;
    if(globalLabelsMap.size() == 0){
        this.globalLabelNames = new ArrayList<String>();
        this.globalLabelValues = new ArrayList<String>();
    }else{
      this.globalLabelNames = new ArrayList<String>(globalLabelsMap.keySet());
      this.globalLabelValues = new ArrayList<String>(globalLabelsMap.values());
    }
  }

  void addThreadMetrics(List<MetricFamilySamples> sampleFamilies) {
    GaugeMetricFamily jvmThreadsCurrent = new GaugeMetricFamily("jvm_threads_current", "Current thread count of a JVM", this.globalLabelNames);
    jvmThreadsCurrent.addMetric(this.globalLabelValues, threadBean.getThreadCount() );
    sampleFamilies.add(jvmThreadsCurrent);
    
    GaugeMetricFamily jvmThreadsDaemon = new GaugeMetricFamily("jvm_threads_daemon", "Daemon thread count of a JVM", this.globalLabelNames);
    jvmThreadsDaemon.addMetric(this.globalLabelValues, threadBean.getDaemonThreadCount() );
    sampleFamilies.add(jvmThreadsDaemon);
    
    GaugeMetricFamily jvmThreadsPeak = new GaugeMetricFamily("jvm_threads_peak", "Peak thread count of a JVM", this.globalLabelNames);
    jvmThreadsPeak.addMetric(this.globalLabelValues, threadBean.getPeakThreadCount() );
    sampleFamilies.add(jvmThreadsPeak);
    
    CounterMetricFamily jvmThreadsStartedTotal = new CounterMetricFamily("jvm_threads_started_total", "Started thread count of a JVM", this.globalLabelNames);
    jvmThreadsStartedTotal.addMetric(this.globalLabelValues, threadBean.getTotalStartedThreadCount());
    sampleFamilies.add(jvmThreadsStartedTotal);
    
    GaugeMetricFamily jvmThreadsDeadlocked = new GaugeMetricFamily("jvm_threads_deadlocked", "Cycles of JVM-threads that are in deadlock waiting to acquire object monitors or ownable synchronizers", this.globalLabelNames);
    jvmThreadsDeadlocked.addMetric(this.globalLabelValues, nullSafeArrayLength(threadBean.findDeadlockedThreads()) );
    sampleFamilies.add(jvmThreadsDeadlocked);
    
    GaugeMetricFamily jvmThreadsDeadlockedMonitor = new GaugeMetricFamily("jvm_threads_deadlocked_monitor", "Cycles of JVM-threads that are in deadlock waiting to acquire object monitors", this.globalLabelNames);
    jvmThreadsDeadlockedMonitor.addMetric(this.globalLabelValues, nullSafeArrayLength(threadBean.findMonitorDeadlockedThreads()));
    sampleFamilies.add(jvmThreadsDeadlockedMonitor);
  }

  private static double nullSafeArrayLength(long[] array) {
    return null == array ? 0 : array.length;
  }

  public List<MetricFamilySamples> collect() {
    List<MetricFamilySamples> mfs = new ArrayList<MetricFamilySamples>();
    addThreadMetrics(mfs);
    return mfs;
  }
}
