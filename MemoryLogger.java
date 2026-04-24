import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;

// MemoryLogger
// - This class provides a method to log the peak heap memory usage.
// - Coded by: Trinh D.D. Nguyen
// - Version 1.0
// - Date: 2025-05-18

public class MemoryLogger {

    public static double peakHeapUsage() {
    	double retVal = 0;
    	try {
            List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
			double total = 0;
			for (MemoryPoolMXBean memoryPoolMXBean : pools)
				if (memoryPoolMXBean.getType() == MemoryType.HEAP)
					total = total + memoryPoolMXBean.getPeakUsage().getUsed();
			retVal = total;
		} catch (Throwable t) {
			System.err.println("Exception: " + t);
		}
    	return retVal/1024/1024;
    }		
    
}