package io.prometheus.jmx.custom.hotspot;

import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Exports JVM version info.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 *   new VersionInfoExports().register();
 * }
 * </pre>
 * Metrics being exported:
 * <pre>
 *   jvm_info{version="1.8.0_151-b12",vendor="Oracle Corporation",runtime="OpenJDK Runtime Environment",} 1.0
 * </pre>
 */

public class VersionInfoExports extends Collector {

    private final List<String> globalLabelNames;
    private final List<String> globalLabelValues;
    
    public VersionInfoExports(Map<String, String> globalLabelsMap) {
      if(globalLabelsMap.size() == 0){
        this.globalLabelNames = new ArrayList<String>();
        this.globalLabelValues = new ArrayList<String>();
      }else{
        this.globalLabelNames = new ArrayList<String>(globalLabelsMap.keySet());
        this.globalLabelValues = new ArrayList<String>(globalLabelsMap.values());
      }
    }

    public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples> mfs = new ArrayList<MetricFamilySamples>();
        
        List<String> labelNames = new ArrayList<String>(globalLabelNames);
        labelNames.addAll(Arrays.asList("version", "vendor", "runtime"));
        
        List<String> labelValues = new ArrayList<String>(globalLabelValues);
        labelValues.addAll(Arrays.asList(
                System.getProperty("java.runtime.version", "unknown"),
                System.getProperty("java.vm.vendor", "unknown"),
                System.getProperty("java.runtime.name", "unknown")));

        GaugeMetricFamily jvmInfo = new GaugeMetricFamily(
                "jvm_info",
                "JVM version info",
                labelNames);
        jvmInfo.addMetric(labelValues, 1L);
        mfs.add(jvmInfo);

        return mfs;
    }
}
