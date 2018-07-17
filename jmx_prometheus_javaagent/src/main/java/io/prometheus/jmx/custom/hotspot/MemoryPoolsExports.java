package io.prometheus.jmx.custom.hotspot;

import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exports metrics about JVM memory areas.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 *   new MemoryPoolsExports().register();
 * }
 * </pre>
 * Example metrics being exported:
 * <pre>
 *   jvm_memory_bytes_used{area="heap"} 2000000
 *   jvm_memory_bytes_committed{area="nonheap"} 200000
 *   jvm_memory_bytes_max{area="nonheap"} 2000000
 *   jvm_memory_pool_bytes_used{pool="PS Eden Space"} 2000
 * </pre>
 */
public class MemoryPoolsExports extends Collector {
  private final MemoryMXBean memoryBean;
  private final List<MemoryPoolMXBean> poolBeans;
  private final List<String> globalLabelNames;
  private final List<String> globalLabelValues;

  public MemoryPoolsExports(Map<String, String> globalLabelsMap) {
    this(
        ManagementFactory.getMemoryMXBean(),
        ManagementFactory.getMemoryPoolMXBeans(), globalLabelsMap);
  }

  public MemoryPoolsExports(MemoryMXBean memoryBean,
                             List<MemoryPoolMXBean> poolBeans, Map<String, String> globalLabelsMap) {
    this.memoryBean = memoryBean;
    this.poolBeans = poolBeans;
    if(globalLabelsMap.size() == 0){
        this.globalLabelNames = new ArrayList<String>();
        this.globalLabelValues = new ArrayList<String>();
    }else{
      this.globalLabelNames = new ArrayList<String>(globalLabelsMap.keySet());
      this.globalLabelValues = new ArrayList<String>(globalLabelsMap.values());
    }
  }

  void addMemoryAreaMetrics(List<MetricFamilySamples> sampleFamilies) {
    MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
    MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

    GaugeMetricFamily used = new GaugeMetricFamily(
        "jvm_memory_bytes_used",
        "Used bytes of a given JVM memory area.",
        listCloneAndAdd(globalLabelNames, "area"));
    used.addMetric(listCloneAndAdd(globalLabelValues, "heap"), heapUsage.getUsed());
    used.addMetric(listCloneAndAdd(globalLabelValues, "nonheap"), nonHeapUsage.getUsed());
    sampleFamilies.add(used);

    GaugeMetricFamily committed = new GaugeMetricFamily(
        "jvm_memory_bytes_committed",
        "Committed (bytes) of a given JVM memory area.",
        listCloneAndAdd(globalLabelNames, "area"));
    committed.addMetric(listCloneAndAdd(globalLabelValues, "heap"), heapUsage.getCommitted());
    committed.addMetric(listCloneAndAdd(globalLabelValues, "nonheap"), nonHeapUsage.getCommitted());
    sampleFamilies.add(committed);

    GaugeMetricFamily max = new GaugeMetricFamily(
        "jvm_memory_bytes_max",
        "Max (bytes) of a given JVM memory area.",
        listCloneAndAdd(globalLabelNames, "area"));
    max.addMetric(listCloneAndAdd(globalLabelValues, "heap"), heapUsage.getMax());
    max.addMetric(listCloneAndAdd(globalLabelValues, "nonheap"), nonHeapUsage.getMax());
    sampleFamilies.add(max);

    GaugeMetricFamily init = new GaugeMetricFamily(
        "jvm_memory_bytes_init",
        "Initial bytes of a given JVM memory area.",
        listCloneAndAdd(globalLabelNames, "area"));
    init.addMetric(listCloneAndAdd(globalLabelValues, "heap"), heapUsage.getInit());
    init.addMetric(listCloneAndAdd(globalLabelValues, "nonheap"), nonHeapUsage.getInit());
    sampleFamilies.add(init);
  }

  void addMemoryPoolMetrics(List<MetricFamilySamples> sampleFamilies) {
    GaugeMetricFamily used = new GaugeMetricFamily(
        "jvm_memory_pool_bytes_used",
        "Used bytes of a given JVM memory pool.",
        listCloneAndAdd(globalLabelNames, "pool"));
    sampleFamilies.add(used);
    GaugeMetricFamily committed = new GaugeMetricFamily(
        "jvm_memory_pool_bytes_committed",
        "Committed bytes of a given JVM memory pool.",
        listCloneAndAdd(globalLabelNames, "pool"));
    sampleFamilies.add(committed);
    GaugeMetricFamily max = new GaugeMetricFamily(
        "jvm_memory_pool_bytes_max",
        "Max bytes of a given JVM memory pool.",
        listCloneAndAdd(globalLabelNames, "pool"));
    sampleFamilies.add(max);
    GaugeMetricFamily init = new GaugeMetricFamily(
        "jvm_memory_pool_bytes_init",
        "Initial bytes of a given JVM memory pool.",
        listCloneAndAdd(globalLabelNames, "pool"));
    sampleFamilies.add(init);
    for (final MemoryPoolMXBean pool : poolBeans) {
      MemoryUsage poolUsage = pool.getUsage();
      used.addMetric(
          listCloneAndAdd(globalLabelValues, pool.getName()),
          poolUsage.getUsed());
      committed.addMetric(
          listCloneAndAdd(globalLabelValues, pool.getName()),
          poolUsage.getCommitted());
      max.addMetric(
          listCloneAndAdd(globalLabelValues, pool.getName()),
          poolUsage.getMax());
      init.addMetric(
          listCloneAndAdd(globalLabelValues, pool.getName()),
          poolUsage.getInit());
    }
  }
  
  private static List<String> listCloneAndAdd(List<String> listOfStr, String element) {
      List<String> combinedList = new ArrayList<>(listOfStr);
      combinedList.add(element);
      return combinedList;
  }

  public List<MetricFamilySamples> collect() {
    List<MetricFamilySamples> mfs = new ArrayList<MetricFamilySamples>();
    addMemoryAreaMetrics(mfs);
    addMemoryPoolMetrics(mfs);
    return mfs;
  }
}
