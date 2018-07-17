package io.prometheus.jmx.custom.hotspot;

import io.prometheus.client.Collector;
import io.prometheus.client.SummaryMetricFamily;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Exports metrics about JVM garbage collectors.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 *   new GarbageCollectorExports().register();
 * }
 * </pre>
 * Example metrics being exported:
 * <pre>
 *   jvm_gc_collection_seconds_count{gc="PS1"} 200
 *   jvm_gc_collection_seconds_sum{gc="PS1"} 6.7
 * </pre>
 */
public class GarbageCollectorExports extends Collector {
  private final List<GarbageCollectorMXBean> garbageCollectors;
  private final List<String> globalLabelNames;
  private final List<String> globalLabelValues;

  public GarbageCollectorExports(Map<String, String> globalLabelsMap) {
    this(ManagementFactory.getGarbageCollectorMXBeans(), globalLabelsMap);
  }

  GarbageCollectorExports(List<GarbageCollectorMXBean> garbageCollectors, Map<String, String> globalLabelsMap) {
    this.garbageCollectors = garbageCollectors;
    if(globalLabelsMap.size() == 0){
        this.globalLabelNames = new ArrayList<String>();
        this.globalLabelValues = new ArrayList<String>();
    }else{
      this.globalLabelNames = new ArrayList<String>(globalLabelsMap.keySet());
      this.globalLabelValues = new ArrayList<String>(globalLabelsMap.values());
    }
  }

  public List<MetricFamilySamples> collect() {
    SummaryMetricFamily gcCollection = new SummaryMetricFamily(
        "jvm_gc_collection_seconds",
        "Time spent in a given JVM garbage collector in seconds.",
        listCloneAndAdd(this.globalLabelNames, "gc"));
    for (final GarbageCollectorMXBean gc : garbageCollectors) {
        gcCollection.addMetric(
        	listCloneAndAdd(this.globalLabelValues, gc.getName()),
            gc.getCollectionCount(),
            gc.getCollectionTime() / MILLISECONDS_PER_SECOND);
    }
    List<MetricFamilySamples> mfs = new ArrayList<MetricFamilySamples>();
    mfs.add(gcCollection);
    return mfs;
  }
  
  private static List<String> listCloneAndAdd(List<String> listOfStr, String element) {
      List<String> combinedList = new ArrayList<>(listOfStr);
      combinedList.add(element);
      return combinedList;
  }
}
