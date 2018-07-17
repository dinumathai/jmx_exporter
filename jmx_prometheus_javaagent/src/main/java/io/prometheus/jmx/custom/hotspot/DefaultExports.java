package io.prometheus.jmx.custom.hotspot;

import java.util.Map;

/**
 * Registers the default Hotspot collectors.
 * <p>
 * This is intended to avoid users having to add in new
 * registrations every time a new exporter is added.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 *   DefaultExports.initialize();
 * }
 * </pre>
 */
public class DefaultExports {
  private static boolean initialized = false;
  /**
   * Register the default Hotspot collectors.
   */
  public static synchronized void initialize(Map<String, String> globalLabels) {
    if (!initialized) {
      new StandardExports(globalLabels).register();
      new MemoryPoolsExports(globalLabels).register();
      new BufferPoolsExports(globalLabels).register();
      new GarbageCollectorExports(globalLabels).register();
      new ThreadExports(globalLabels).register();
      new ClassLoadingExports(globalLabels).register();
      new VersionInfoExports(globalLabels).register();
      initialized = true;
    }
  }

}
