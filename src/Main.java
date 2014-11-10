import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class Main
{
	/**
	 * executor for http requests
	 */
	private final ThreadPoolExecutor pool = new ThreadPoolExecutor(3, 10, 10, TimeUnit.MINUTES,
			new ArrayBlockingQueue<Runnable>(100), new ThreadFactory()
	{
		/**
		 * group
		 */
		private final ThreadGroup group = new ThreadGroup(Thread.currentThread().getThreadGroup(), "HttpStatWorker");
		/**
		 * incrementor
		 */
		private int count = 0;

		@Override
		public Thread newThread(final Runnable r)
		{
			final Thread th = new Thread(group, r);
			th.setDaemon(true);
			th.setName("HttpStatWorker-" + count++);
			return th;
		}
	});
	/**
	 * server ref
	 */
	private HttpServer server;
	private final DonationTable donationTable;

	public Main() throws ParseException, IOException
	{
		donationTable = new DonationTable(new File("data"));
	}

	public static void main(final String[] args) throws Exception
	{
		if (args.length > 0 && "server".equals(args[0]))
		{
			new Main().startServer();
		}
		else
		{
			requestData();
		}
	}

	private void startServer() throws IOException
	{
		server = HttpServer.create(new InetSocketAddress(8086), 5);
		server.setExecutor(pool);
		server.createContext("/", new HttpHandler()
		{
			@Override
			public void handle(final HttpExchange exc) throws IOException
			{
				try
				{
					exc.getResponseHeaders().add("Cache-Control",
							"no-cache, no-store, must-revalidate, proxy-revalidate, max-age=0, s-maxage=0");
					exc.getResponseHeaders().add("Pragma", "no-cache");

					final Properties props = new Properties();
					if (exc.getRequestURI().getQuery() != null)
						for (final String pair : exc.getRequestURI().getQuery().split("&"))
						{
							final String[] keyval = pair.split("=");
							props.put(keyval[0], keyval[1]);
						}

					// final String rootBag = props.getProperty("r", "groupserver");

					final String res = donationTable.renderView(props);
					final byte[] bytes = res.getBytes();
					// exc.getResponseHeaders().add("Content-Type", "text/plain;charset=ISO-8859-1");
					exc.sendResponseHeaders(200, bytes.length);
					exc.getResponseBody().write(bytes);
					exc.close();
					return;
				}
				catch (final Exception e)
				{
					e.printStackTrace();
				}
				finally
				{
					exc.sendResponseHeaders(503, -1);
					exc.close();
				}
			}
		});
		server.start();

//		server.stop(0);
	}

	public static void requestData() throws Exception
	{
		// TODO
		final String nickname = "";
		final String password = "";

		// @formatter:off
		final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager(){
			@Override	public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException{}
			@Override	public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException{}
			@Override public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}}};
		final SSLContext sc = SSLContext.getInstance("TLS");
		sc.init(null, trustAllCerts, new SecureRandom());
		// @formatter:on

		final HttpContext ctx = new BasicHttpContext();
		ctx.setAttribute(CoreProtocolPNames.USER_AGENT,
				"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/31.0.1650.63 Safari/537.36");
		final CloseableHttpClient cl = createClient(sc);

		// get auth
		final HttpPost httpost = addHeader(new HttpPost("https://auth.startrekonline.com/"));
		final List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("user", nickname));
		nvps.add(new BasicNameValuePair("pw", password));
		httpost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));

		final HttpEntity entity = cl.execute(httpost, ctx).getEntity();
		String resp = toString(entity);
		// "idBrowser":"H7rCSledmRjmA1k3ZdX7CR5WE3WVMxOX00RsCp017","idTicket":984206736,"idAccount":6653320
		final JSONObject authjn = new JSONObject(resp);
		final String idBrowser = authjn.getString("idBrowser");
		final String idTicket = "" + authjn.getInt("idTicket");
		final String idAccount = "" + authjn.getInt("idAccount");

		// get remote time
		toString(cl.execute(addHeader(new HttpGet("http://gateway.startrekonline.com/time")), ctx).getEntity());

		// req data tables
		resp = toString(cl.execute(addHeader(new HttpGet("http://gateway.startrekonline.com/dataTables.js")), ctx)
				.getEntity());

		// get gateway cookie
		final String uri = "http://gateway.startrekonline.com/?account=" + idAccount + "&ticket=" + idTicket + "&browser="
				+ idBrowser + "";
		toString(cl.execute(addHeader(new HttpGet(uri)), ctx).getEntity());

		// get pool token
		resp = toString(cl.execute(addHeader(new HttpGet("http://gateway.startrekonline.com/socket.io/1/?t=1415495664634")),
				ctx).getEntity());
		final String tok = resp.split(":", 2)[0];

		// init and succ login
		getPool(cl, tok, ctx, "1:");
		getPool(cl, tok, ctx, "LoginSuccess");

		// start protocol
		final String reqDefRsc = "5:::{\"name\":\"Client_RequestDefaultResource\",\"args\":[{\"dictname\":\"CharacterClass\"}]}";
		sendPost(cl, tok, reqDefRsc, ctx);
		getPool(cl, tok, ctx, "DefaultResource");

		final String reqRsc = "5:::{\"name\":\"Client_RequestResource\",\"args\":[{\"dictname\":\"CharacterClass\",\"id\":\"Starfleet_Science\"}]}";
		sendPost(cl, tok, reqRsc, ctx);
		getPool(cl, tok, ctx, "Proxy_Resource");

		final String reqRsc2 = "5:::{\"name\":\"Client_RequestResource\",\"args\":[{\"dictname\":\"CharacterClass\",\"id\":\"Dreadnought_Scimitar_Tac_T5u\"}]}";
		sendPost(cl, tok, reqRsc2, ctx);
		getPool(cl, tok, ctx, "Proxy_Resource");

		final String sendTick = "5:::{\"name\":\"Client_SendAnalyticTick\",\"args\":[{\"bucket\":\"User:CharacterSelect\",\"count\":1}]}";
		sendPost(cl, tok, sendTick, ctx);

		final String reqEnt = "5:::{\"name\":\"Client_RequestEntity\",\"args\":[{\"id\":\"Xyan Karii@xyan2\",\"params\":{}}]}";
		sendPost(cl, tok, reqEnt, ctx);
		getPool(cl, tok, ctx, "Proxy_LoginEntity");

		getPool(cl, tok, ctx, "Proxy_Pet");

		// select me
		toString(cl.execute(addHeader(new HttpGet("http://gateway.startrekonline.com/ent/me")), ctx).getEntity());

		final String reqPersPro = "5:::{\"name\":\"Client_RequestPersonalProject\",\"args\":[{\"id\":\"4551030\",\"params\":{}}]}";
		sendPost(cl, tok, reqPersPro, ctx);
		getPool(cl, tok, ctx, "Proxy_PersonalProject");

		sendPost(cl, tok, "5:::{\"name\":\"Client_RequestGuild\",\"args\":[{\"id\":\"Raumflotte\",\"params\":{}}]}", ctx);
		getPool(cl, tok, ctx, "Proxy_Guild");

		// get final data
		sendPost(cl, tok, "5:::{\"name\":\"Client_RequestGroupProject\",\"args\":[{\"id\":\"2909\",\"params\":{}}]}", ctx);
		final String awn = getPool(cl, tok, ctx, "Proxy_GroupProject");

		saveData(awn);
	}

	private static void saveData(final String awn) throws IOException
	{
		final File root = new File("data");
		if (!root.isDirectory())
			root.mkdirs();
		final SimpleDateFormat df = new SimpleDateFormat("yyMMdd_HHmmss");
		final File target = new File(root, df.format(new Date()) + ".txt");
		// args -> 0 -> container -> states -> X -> donationstats[]
		// 0 Botschaft, 1 Mine, 2 Spire, 3 Basis
		// {id:1386, displayname:Joran Brixs@Brixs, contribution:28125}
		FileWriter fw = null;
		try
		{
			fw = new FileWriter(target);
			final JSONObject dat = new JSONObject(awn.substring(4));
			final JSONArray states = ((JSONObject) dat.getJSONArray("args").get(0)).getJSONObject("container").getJSONArray(
					"states");
			for (int i = 0; i < states.length(); i++)
			{
				fw.write("\t=====  " + i + "  =====\n");
				final JSONArray dons = ((JSONObject) states.get(i)).getJSONArray("donationstats");
				for (int i2 = 0; i2 < dons.length(); i2++)
				{
					final JSONObject don = (JSONObject) dons.get(i2);
					final int id = don.getInt("id");
					final String name = don.getString("displayname");
					final int credits = don.getInt("contribution");
					fw.write(i + ", " + id + ", " + name + ", " + credits + "\n");
				}
			}
		}
		finally
		{
			fw.close();
		}
	}

	public static String toString(final HttpEntity ent) throws IllegalStateException, IOException
	{
		final String res = IOUtils.toString(ent.getContent());
		EntityUtils.consumeQuietly(ent);
		return res;
	}

	private static <R extends HttpRequestBase> R addHeader(final R req)
	{
		req.addHeader("Referer", "http://gateway.startrekonline.com/");
		req.addHeader("Accept", "*/*");
		req.addHeader("Accept-Encoding", "gzip,deflate,sdch");
		req.addHeader("Accept-Language", "de-DE,de;q=0.8,en-US;q=0.6,en;q=0.4");
		req.addHeader("Connection", "keep-alive");
		req.addHeader("X-Requested-With", "XMLHttpRequest");
		return req;
	}

	private static CloseableHttpClient createClient(final SSLContext sc)
	{
		final SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sc, new String[] { "TLSv1" }, null,
				SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
		final PoolingHttpClientConnectionManager poolingmgr = new PoolingHttpClientConnectionManager(RegistryBuilder
				.<ConnectionSocketFactory> create().register("http", PlainConnectionSocketFactory.getSocketFactory())
				.register("https", sslsf).build());
		final HttpClientBuilder custom = HttpClients.custom();
		custom.setSSLSocketFactory(sslsf);
		custom.disableAutomaticRetries();
		custom.disableRedirectHandling();
		final SocketConfig socketConfig = SocketConfig.custom().setTcpNoDelay(true).setSoKeepAlive(true)
				.setSoReuseAddress(true).build();
		poolingmgr.setDefaultSocketConfig(socketConfig);
		custom.setConnectionManager(poolingmgr);
		final CloseableHttpClient cl = custom.build();
		return cl;
	}

	private static String getPool(final CloseableHttpClient cl, final String btok, final HttpContext ctx,
			final String contains) throws IOException, ClientProtocolException, InterruptedException
	{
		final HttpGet httpget = addHeader(new HttpGet("http://gateway.startrekonline.com/socket.io/1/xhr-polling/" + btok
				+ "?t=" + System.currentTimeMillis()));
		for (int i = 0; i < 2; i++)
		{
			final String awn = toString(cl.execute(httpget, ctx).getEntity());
			if (awn.contains(contains))
			{
				System.out.println("<<\t" + awn.substring(0, Math.min(300, awn.length() - 1)));
				return awn;
			}
			else
			{
				System.out.println("!!!\t" + awn.substring(0, Math.min(300, awn.length() - 1)));
			}
		}
		return null;
	}

	private static void sendPost(final CloseableHttpClient cl, final String btok, final String dat, final HttpContext ctx)
			throws UnsupportedEncodingException, IOException, ClientProtocolException, InterruptedException
	{
		Thread.sleep(50);
		final HttpPost httpost = addHeader(new HttpPost("http://gateway.startrekonline.com/socket.io/1/xhr-polling/" + btok
				+ "?t=" + System.currentTimeMillis()));
		httpost.setEntity(new StringEntity(dat));

		final String str = toString(cl.execute(httpost, ctx).getEntity());
		System.out.println(">>\t" + dat);
	}
}
