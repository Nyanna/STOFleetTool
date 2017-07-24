package net.xy.sto.loottool;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import net.xy.sto.loottool.STOData.AbstractData;
import net.xy.sto.loottool.STOData.ItemReceived;
import net.xy.sto.loottool.STOData.NumericReceived;

public class ChatLogParser {
	private static final File chatlog = new File(
			"D:\\Games\\Star Trek Online\\Star Trek Online_de\\Star Trek Online\\Live\\logs\\GameClient\\Chat.Log");
	private final Map<String, Class<? extends AbstractData>> datamap = new HashMap<>();

	private final ExecutorService exec = new ScheduledThreadPoolExecutor(10, new ThreadFactory() {
		@Override
		public Thread newThread(final Runnable r) {
			final Thread t = new Thread(r);
			t.setDaemon(true);
			return t;
		}
	});
	private final DataStore store = new DataStore();
	private final AtomicInteger excount = new AtomicInteger();

	public ChatLogParser() {
		datamap.put("ItemReceived@", ItemReceived.class);
		datamap.put("NumericReceived@", NumericReceived.class);
	}

	public String parse(final File src, final DoffDB doffdb) throws Exception {
		final BufferedReader br = new BufferedReader(
				new InputStreamReader(new BufferedInputStream(new FileInputStream(src)), "UTF-8"));
		try {
			String line;
			while ((line = br.readLine()) != null)
				parseLineAsync(line);
		} finally {
			br.close();
		}
		while (excount.get() != 0)
			Thread.sleep(10);

		return store.printSummary(doffdb);
	}

	public static class MyHandler implements HttpHandler {
		final DoffDB doffdb = new DoffDB();

		public MyHandler() {
			doffdb.load();
			System.out.println("DoffDB loaded");
		}

		@Override
		public void handle(final HttpExchange t) throws IOException {

			final ChatLogParser parser = new ChatLogParser();
			String res = null;
			try {
				res = parser.parse(chatlog, doffdb);
			} catch (final Exception e) {
				e.printStackTrace();
			}
			final String response = "<html><body>" + res + "</body></html>";

			t.sendResponseHeaders(200, response.length());
			final OutputStream os = t.getResponseBody();
			os.write(response.getBytes());
			os.close();
		}
	}

	private void parseLineAsync(final String line) {
		excount.incrementAndGet();
		exec.submit(new Runnable() {
			@Override
			public void run() {
				try {
					parseLine(line);
				} catch (final Exception e) {
					e.printStackTrace();
				} finally {
					excount.decrementAndGet();
				}
			}
		});
	}

	private void parseLine(final String line) throws InstantiationException, IllegalAccessException {
		if (!line.startsWith("["))
			return;

		final int start = findOccurance(line, 0, 3);
		final int endKey = line.indexOf(",", start + 1);
		final String key = line.substring(start + 1, endKey);
		final AbstractData data = getData(key);
		if (data == null)
			return;

		final int starts = line.indexOf("]", endKey);
		data.setText(line.substring(starts + 1));
		store.addData(data);
	}

	private AbstractData getData(final String key) throws InstantiationException, IllegalAccessException {
		final Class<? extends AbstractData> res = datamap.get(key);
		if (res == null)
			return null;
		return res.newInstance();
	}

	private int findOccurance(final String str, final int start, final int ocur) {
		int idx = start;
		for (int i = 0; i < ocur; i++)
			idx = str.indexOf(",", idx + 1);
		return idx;
	}

	public static void main(final String[] args) throws Exception {
		final HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
		server.createContext("/", new MyHandler());
		server.start();
		Runtime.getRuntime().exec("cmd /c start \"\" http://localhost:8000/");

		Thread.sleep(6000 * 1000);
	}
}
