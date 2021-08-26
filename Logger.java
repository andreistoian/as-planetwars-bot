import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: andrei
 * Date: Oct 21, 2010
 * Time: 9:11:47 PM
 * To change this template use File | Settings | File Templates.
 */
public class Logger {
	static boolean debug = true;
	static boolean perf = true;

	static private Socket debugSoc;
	static private PrintWriter debugPW;

	static class PerfData {
		public long start, total;
		public Map<String, PerfData> counters;
		String name;
		PerfData parent;
		int iter;
	}

	static private PerfData m_PerfRoot;
	static private PerfData m_CurrentPerfCounter;

	public static void AppendLog(String data)
	{
		AppendLog(data, true);
	}

	public static void ResetLog(boolean dbg, boolean prf) {
		debug = dbg;
		perf = prf;		
		if (debug)
		{
			File f = new File("andrei.txt");
			f.delete();

			f = new File("debug.txt");
			f.delete();

			try {
				debugSoc = new Socket("localhost", 10001);
				debugPW = new PrintWriter(debugSoc.getOutputStream());
			} catch (Exception ignore) {
			}
		}

		if (perf)
		{
			File f = new File("perflog.txt");
			f.delete();			
		}
	}

	public static void CloseLog() {
		if (debug)
		{
			debugPW.close();
			try {
				debugSoc.close();
			} catch (Exception ignore) {
			}
		}		
	}

	public static void ResetPerfCounters() {
		if (perf) {
			m_CurrentPerfCounter = null;
		}
	}

	public static void StartPerfLog(String name)
	{
		if (perf) {
			if (m_CurrentPerfCounter != null && m_CurrentPerfCounter.counters.containsKey(name)) {
				PerfData pd = m_CurrentPerfCounter.counters.get(name);
				pd.start = System.nanoTime();
				pd.iter++;
				m_CurrentPerfCounter = pd;
			}
			else
			{
				PerfData pd = new PerfData();
				pd.start = System.nanoTime();
				pd.parent = m_CurrentPerfCounter;
				pd.counters = new HashMap<String, PerfData>();
				pd.name = name;
				pd.iter = 1;
				if (m_CurrentPerfCounter != null)
					m_CurrentPerfCounter.counters.put(name, pd);
				else
					m_PerfRoot = pd;
				m_CurrentPerfCounter = pd;
			}
		}
	}

	public static void EndPerfLog(String msg)
	{
		if (perf) {
			PerfData pd = m_CurrentPerfCounter;

			pd.total += System.nanoTime() - pd.start;
			pd.start = 0;
			m_CurrentPerfCounter = m_CurrentPerfCounter.parent;
		}
	}

	private static void WritePerfLogRec(PrintWriter pw, PerfData pd, String pad) {
		
		pw.println(pad + pd.name + "(" + pd.iter + ")" + ": " + pd.total / 1000000.f + "ms");
		for (String k : pd.counters.keySet())
		{
			WritePerfLogRec(pw, pd.counters.get(k), pad + "  ");
		}
	}

	public static void WritePerfLog()
	{
		if (perf) {
			try {
				PrintWriter dbgpw;
				dbgpw = new PrintWriter(new FileWriter("perflog.txt", true));
				WritePerfLogRec(dbgpw, m_PerfRoot, "");
				dbgpw.println();
				dbgpw.close();
			} catch (IOException err) {
				System.out.println("ERROR");
			}
		}
	}

	public static void AppendLog(String data, boolean sock)
	{
		if (debug)
		{
			try {
				PrintWriter dbgpw;

				if (sock) {
					dbgpw = new PrintWriter(new FileWriter("andrei.txt", true));
					dbgpw.println(data);
					dbgpw.close();
				}

				dbgpw = new PrintWriter(new FileWriter("debug.txt", true));
				dbgpw.println(data);
				dbgpw.flush();
				dbgpw.close();

			} catch (IOException err) {
				System.out.println("ERROR");
			}

			if (sock)
			{
				try {
					if (debugPW != null)
					{
						debugPW.println(data);
						debugPW.flush();
					}
				} catch (Exception ignore) {
				}
			}
		}
	}

	public static void PrintException(Exception e) {
		if (debug) {
			try {
				PrintWriter dbgpw = new PrintWriter(new FileWriter("debug.txt", true));
				e.printStackTrace(dbgpw);
				dbgpw.close();
			} catch (IOException ignore) {
			}
		}
	}
}
