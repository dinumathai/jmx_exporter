package io.prometheus.jmx.custom.hotspot;

import io.prometheus.client.Collector;
import io.prometheus.client.CounterMetricFamily;
import io.prometheus.client.GaugeMetricFamily;

import java.lang.management.ManagementFactory;
import java.lang.management.ClassLoadingMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exports metrics about JVM classloading.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 *   new ClassLoadingExports().register();
 * }
 * </pre>
 * Example metrics being exported:
 * <pre>
 *   jvm_classes_loaded{} 1000
 *   jvm_classes_loaded_total{} 2000
 *   jvm_classes_unloaded_total{} 500
 * </pre>
 */
public class ClassLoadingExports extends Collector {
  private final ClassLoadingMXBean clBean;
  private final List<String> globalLabelNames;
  private final List<String> globalLabelValues;

  public ClassLoadingExports(Map<String, String> globalLabelsMap) {
    this(ManagementFactory.getClassLoadingMXBean(), globalLabelsMap);
  }

  public ClassLoadingExports(ClassLoadingMXBean clBean, Map<String, String> globalLabelsMap) {
    this.clBean = clBean;
    if(globalLabelsMap.size() == 0){
        this.globalLabelNames = new ArrayList<String>();
        this.globalLabelValues = new ArrayList<String>();
    }else{
      this.globalLabelNames = new ArrayList<String>(globalLabelsMap.keySet());
      this.globalLabelValues = new ArrayList<String>(globalLabelsMap.values());
    }
  }

  void addClassLoadingMetrics(List<MetricFamilySamples> sampleFamilies) {
	  
    GaugeMetricFamily jvmClassesLoaded = new GaugeMetricFamily("jvm_classes_loaded", "The number of classes that are currently loaded in the JVM", this.globalLabelNames);
    jvmClassesLoaded.addMetric(this.globalLabelValues, clBean.getLoadedClassCount());
    sampleFamilies.add(jvmClassesLoaded);
	
    CounterMetricFamily jvmClassesLoadedTotal = new CounterMetricFamily("jvm_classes_loaded_total", "The total number of classes that have been loaded since the JVM has started execution", this.globalLabelNames);
    jvmClassesLoadedTotal.addMetric(this.globalLabelValues, clBean.getTotalLoadedClassCount() );
    sampleFamilies.add(jvmClassesLoadedTotal);
    
    CounterMetricFamily jvmClassesUnloadedTotal = new CounterMetricFamily("jvm_classes_unloaded_total", "The total number of classes that have been unloaded since the JVM has started execution", this.globalLabelNames);
    jvmClassesUnloadedTotal.addMetric(this.globalLabelValues, clBean.getUnloadedClassCount() );
    sampleFamilies.add(jvmClassesUnloadedTotal);
  }

  public List<MetricFamilySamples> collect() {
    List<MetricFamilySamples> mfs = new ArrayList<MetricFamilySamples>();
    addClassLoadingMetrics(mfs);
    return mfs;
  }
}
