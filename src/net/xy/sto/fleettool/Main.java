package net.xy.sto.fleettool;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
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
import org.json.JSONObject;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Main entry point
 *
 * @author Xyan
 *
 */
public class Main
{
	private static final String GATEWAY = "http://gateway.startrekonline.com";
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
			th.setName("HttpWorker-" + count++);
			return th;
		}
	});
	/**
	 * server ref
	 */
	private HttpServer server;
	/**
	 * data store parser and renderer
	 */
	private final DonationTable donationTable;
	private final ProjectTable projectTable;

	/**
	 * default
	 */
	public Main() throws ParseException, IOException
	{
		donationTable = new DonationTable(new File("data"));
		projectTable = new ProjectTable(new File("data"));
	}

	/**
	 * main
	 */
	public static void main(final String[] args) throws Exception
	{
		if (args.length > 0 && "server".equals(args[0]))
			new Main().startServer();
		else
			requestData();
	}

	/**
	 * simply starts an webserver
	 */
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

					final String stat = props.getProperty("s", "user");

					final StringBuilder sb = new StringBuilder();
					sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"\r\n"
							+ "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\r\n"
							+ "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\">\r\n" + "<head>");
					sb.append("<style type=\"text/css\">\n" //
							+ "	* {font-family:\"arial\";font-size:12px;} \n" //
							+ "	td {border-bottom:1px solid #AAAAAA;border-right:1px solid #AAAAAA;} \n" //
							+ "	.credits {text-align:right;} \n" //
							+ "	.date, .nick {font-weight:bold;} \n" //
							+ "	.department {font-weight:bold;text-decoration:underline;} \n" //
							+ "	.task {font-weight:bold;} \n" //
							+ "		</style>");
					sb.append("</head>");
					sb.append("<body>");
					final String res = stat.equals("user") ? donationTable.renderView(props) : projectTable
							.renderView(props);
					sb.append(res);
					sb.append("</body></html>");

					final byte[] bytes = sb.toString().getBytes();
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

		// server.stop(0);
	}

	/**
	 * does the gateway requests and stores the result
	 */
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
		EntityUtils.consumeQuietly(cl.execute(addHeader(new HttpGet(GATEWAY + "/time")), ctx).getEntity());

		// req data tables
		EntityUtils.consumeQuietly(cl.execute(addHeader(new HttpGet(GATEWAY + "/dataTables.js")), ctx).getEntity());

		// get gateway cookie
		final String uri = GATEWAY + "/?account=" + idAccount + "&ticket=" + idTicket + "&browser=" + idBrowser + "";
		EntityUtils.consumeQuietly(cl.execute(addHeader(new HttpGet(uri)), ctx).getEntity());

		// get pool token
		resp = toString(cl.execute(addHeader(new HttpGet(GATEWAY + "/socket.io/1/?t=1415495664634")), ctx).getEntity());
		final String tok = resp.split(":", 2)[0];

		// init and succ login
		pollFor(cl, tok, ctx, "1:");
		pollFor(cl, tok, ctx, "LoginSuccess");

		// start protocol
		final String reqDefRsc = "5:::{\"name\":\"Client_RequestDefaultResource\",\"args\":[{\"dictname\":\"CharacterClass\"}]}";
		sendPost(cl, tok, reqDefRsc, ctx);
		pollFor(cl, tok, ctx, "DefaultResource");

		final String reqRsc = "5:::{\"name\":\"Client_RequestResource\",\"args\":[{\"dictname\":\"CharacterClass\",\"id\":\"Starfleet_Science\"}]}";
		sendPost(cl, tok, reqRsc, ctx);
		pollFor(cl, tok, ctx, "Proxy_Resource");

		final String reqRsc2 = "5:::{\"name\":\"Client_RequestResource\",\"args\":[{\"dictname\":\"CharacterClass\",\"id\":\"Dreadnought_Scimitar_Tac_T5u\"}]}";
		sendPost(cl, tok, reqRsc2, ctx);
		pollFor(cl, tok, ctx, "Proxy_Resource");

		final String sendTick = "5:::{\"name\":\"Client_SendAnalyticTick\",\"args\":[{\"bucket\":\"User:CharacterSelect\",\"count\":1}]}";
		sendPost(cl, tok, sendTick, ctx);

		final String reqEnt = "5:::{\"name\":\"Client_RequestEntity\",\"args\":[{\"id\":\"Xyan Karii@xyan2\",\"params\":{}}]}";
		sendPost(cl, tok, reqEnt, ctx);
		pollFor(cl, tok, ctx, "Proxy_LoginEntity");

		pollFor(cl, tok, ctx, "Proxy_Pet");

		// select me
		EntityUtils.consumeQuietly(cl.execute(addHeader(new HttpGet(GATEWAY + "/ent/me")), ctx).getEntity());

		final String reqPersPro = "5:::{\"name\":\"Client_RequestPersonalProject\",\"args\":[{\"id\":\"4551030\",\"params\":{}}]}";
		sendPost(cl, tok, reqPersPro, ctx);
		pollFor(cl, tok, ctx, "Proxy_PersonalProject");

		sendPost(cl, tok, "5:::{\"name\":\"Client_RequestGuild\",\"args\":[{\"id\":\"Raumflotte\",\"params\":{}}]}", ctx);
		pollFor(cl, tok, ctx, "Proxy_Guild");

		// get final data
		sendPost(cl, tok, "5:::{\"name\":\"Client_RequestGroupProject\",\"args\":[{\"id\":\"2909\",\"params\":{}}]}", ctx);
		final String awn = pollFor(cl, tok, ctx, "Proxy_GroupProject");

		final Date now = new Date();
		DonationTable.saveData(awn, now);
		ProjectTable.saveData(awn, now);
	}

	/**
	 * reads an request entity to an string
	 */
	public static String toString(final HttpEntity ent) throws IllegalStateException, IOException
	{
		final String res = IOUtils.toString(ent.getContent());
		EntityUtils.consumeQuietly(ent);
		return res;
	}

	/**
	 * add an default header set
	 */
	private static <R extends HttpRequestBase> R addHeader(final R req)
	{
		req.addHeader("Referer", GATEWAY + "/");
		req.addHeader("Accept", "*/*");
		req.addHeader("Accept-Encoding", "gzip,deflate,sdch");
		req.addHeader("Accept-Language", "de-DE,de;q=0.8,en-US;q=0.6,en;q=0.4");
		req.addHeader("Connection", "keep-alive");
		req.addHeader("X-Requested-With", "XMLHttpRequest");
		return req;
	}

	/**
	 * creates and preconfigures an http client
	 *
	 * @param sc
	 * @return
	 */
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

	/**
	 * pool the awnser socket for messages
	 *
	 * @param cl
	 * @param btok
	 * @param ctx
	 * @param contains
	 * @return
	 * @throws IOException
	 * @throws ClientProtocolException
	 * @throws InterruptedException
	 */
	private static String pollFor(final CloseableHttpClient cl, final String btok, final HttpContext ctx,
			final String contains) throws IOException, ClientProtocolException, InterruptedException
	{
		final HttpGet httpget = addHeader(new HttpGet(GATEWAY + "/socket.io/1/xhr-polling/" + btok + "?t="
				+ System.currentTimeMillis()));
		for (int i = 0; i < 2; i++)
		{
			final String awn = toString(cl.execute(httpget, ctx).getEntity());
			if (awn.contains(contains))
			{
				System.out.println("<<\t" + awn.substring(0, Math.min(300, awn.length() - 1)));
				return awn;
			}
			else
				System.out.println("!!!\t" + awn.substring(0, Math.min(300, awn.length() - 1)));
		}
		return null;
	}

	/**
	 * sends an post request to the gateway
	 *
	 * @param cl
	 * @param btok
	 * @param dat
	 * @param ctx
	 * @throws UnsupportedEncodingException
	 * @throws IOException
	 * @throws ClientProtocolException
	 * @throws InterruptedException
	 */
	private static void sendPost(final CloseableHttpClient cl, final String btok, final String dat, final HttpContext ctx)
			throws UnsupportedEncodingException, IOException, ClientProtocolException, InterruptedException
	{
		Thread.sleep(50);
		final HttpPost httpost = addHeader(new HttpPost(GATEWAY + "/socket.io/1/xhr-polling/" + btok + "?t="
				+ System.currentTimeMillis()));
		httpost.setEntity(new StringEntity(dat));

		final String str = toString(cl.execute(httpost, ctx).getEntity());
		System.out.println(">>\t" + dat);
	}
}
