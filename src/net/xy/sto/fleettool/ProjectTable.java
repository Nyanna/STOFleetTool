package net.xy.sto.fleettool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * parser, store and renderer for fleet data
 *
 * @author Xyan
 *
 */
public class ProjectTable
{
	/**
	 * dateformat file pattern for project data
	 */
	public static final SimpleDateFormat DF_FLEET_PROJECTS = new SimpleDateFormat("yyMMdd_HHmmss");
	/**
	 * data store, date -> department -> project -> bucket -> current/total
	 */
	private final Map<Date, Map<Integer, Map<String, Map<String, Integer[]>>>> store = new TreeMap<Date, Map<Integer, Map<String, Map<String, Integer[]>>>>();
	/**
	 * data root directory
	 */
	private final File root;

	/**
	 * default
	 *
	 * @param root
	 * @throws ParseException
	 * @throws IOException
	 */
	public ProjectTable(final File root) throws ParseException, IOException
	{
		this.root = root;
	}

	/**
	 * scans and parses the data directory for data
	 *
	 * @param root
	 * @throws ParseException
	 * @throws IOException
	 */
	private void parseData(final File root) throws ParseException, IOException
	{
		for (final File file : root.listFiles(new FileFilter()
		{
			@Override
			public boolean accept(final File pathname)
			{
				return pathname.getName().endsWith(".proj.txt");
			}
		}))
		{
			final Date fd = DF_FLEET_PROJECTS.parse(file.getName().substring(0, file.getName().length() - 5));
			parseFile(file, fd);
		}
	}

	/**
	 * parses fleet data from the data directory
	 *
	 * @param file
	 * @param fd
	 * @throws IOException
	 */
	private void parseFile(final File file, final Date fd) throws IOException
	{
		final BufferedReader buf = new BufferedReader(new InputStreamReader(new FileInputStream(file), "latin1"));
		try
		{
			Map<Integer, Map<String, Map<String, Integer[]>>> dat = store.get(fd);
			if (dat == null)
			{
				dat = new TreeMap<Integer, Map<String, Map<String, Integer[]>>>();
				store.put(fd, dat);
			}
			Map<String, Map<String, Integer[]>> dep = null;
			Map<String, Integer[]> task = null;

			String line;
			while ((line = buf.readLine()) != null)
			{
				line = line.trim();
				if (line.length() <= 0)
					continue;
				else if (line.startsWith("Department:"))
				{
					final Integer depnr = Integer.valueOf(line.substring("Department:".length()).trim());
					dep = dat.get(depnr);
					if (dep == null)
					{
						dep = new TreeMap<String, Map<String, Integer[]>>();
						dat.put(depnr, dep);
					}
				}
				else if (line.startsWith("Task"))
				{
					final String taskname = line.substring("Task:".length()).trim();
					task = dep.get(taskname);
					if (task == null)
					{
						task = new TreeMap<String, Integer[]>();
						dep.put(taskname, task);
					}
				}
				else
				{
					final String[] namevals = line.split(":");
					final String[] curtot = namevals[1].split("/");
					task.put(namevals[0].trim(), new Integer[] { Integer.valueOf(curtot[0]), Integer.valueOf(curtot[1]) });
				}
			}
		}
		finally
		{
			buf.close();
		}
	}

	/**
	 * renders html with the given properties
	 *
	 * @param props
	 * @return
	 */
	public String renderView(final Properties props)
	{
		store.clear();
		try
		{
			parseData(root);
		}
		catch (final Exception e)
		{
			e.printStackTrace();
		}
		final StringBuilder sb = new StringBuilder();
		sb.append("<table border=\"0\" padding=\"0\" spacing=\"0\">\n");

		final TreeSet<Date> dates = new TreeSet<Date>(store.keySet());
		final List<Date> datesrev = new ArrayList<Date>(dates);
		Collections.reverse(datesrev);

		sb.append("<tr>");
		final DateFormat df = new SimpleDateFormat("MM.dd HH:mm");
		for (final Date hd : datesrev)
		{
			sb.append("<td class=\"date\">");
			sb.append(df.format(hd));
			sb.append("</td>");
		}
		sb.append("</tr>");

		for (int dep = 0; dep <= 3; dep++)
		{
			final StringBuilder dtr = new StringBuilder();
			dtr.append("<tr>");
			for (final Date hd : datesrev)
				dtr.append("<td class=\"department\">" + getName(dep) + "</td>");
			dtr.append("</tr>");
			sb.append(dtr);
			for (int task = 1; task <= 4; task++)
			{
				final StringBuilder tr = new StringBuilder();
				tr.append("<tr>");
				for (final Date hd : datesrev)
				{
					final Map<String, Map<String, Integer[]>> depd = store.get(hd).get(dep);
					final ArrayList<String> tasks;
					if (depd != null && (tasks = new ArrayList<String>(depd.keySet())).size() >= task)
						tr.append("<td class=\"task\" title=\"" + tasks.get(task - 1) + "\">"
								+ tasks.get(task - 1).substring(0, Math.min(15, tasks.get(task - 1).length())) + "</td>");
					else
						tr.append("<td class=\"task\">" + "" + "</td>");
				}
				tr.append("</tr>");
				sb.append(tr);
				for (int bucket = 1; bucket <= 6; bucket++)
				{
					final StringBuilder br = new StringBuilder();
					br.append("<tr>");
					for (final Date hd : datesrev)
					{
						final Map<String, Map<String, Integer[]>> depd = store.get(hd).get(dep);
						if (depd != null)
						{
							final ArrayList<String> tasks = new ArrayList<String>(depd.keySet());
							if (tasks.size() >= task)
							{
								final String taskname = tasks.get(task - 1);
								final Map<String, Integer[]> bucketdat = depd.get(taskname);
								if (bucketdat != null)
								{
									final ArrayList<String> bucketnames = new ArrayList<String>(bucketdat.keySet());
									if (bucketnames.size() >= bucket)
									{
										final String bucketname = bucketnames.get(bucket - 1);
										final Integer[] bval = bucketdat.get(bucketname);
										br.append("<td class=\"bucket\" title=\"" + bucketname + "\">" + bval[0] + "/"
												+ bval[1] + "</td>");
										continue;
									}
								}
							}
						}
						br.append("<td class=\"bucket\">" + "" + "</td>");
					}
					br.append("</tr>");
					sb.append(br);
				}
			}
		}
		sb.append("</table>\n");
		return sb.toString();
	}

	/**
	 * gets the department name from its index
	 *
	 * @param key
	 * @return
	 */
	private String getName(final Integer key)
	{
		// 0 Botschaft, 1 Mine, 2 Spire, 3 Basis
		switch (key)
		{
		case 0:
			return "Embassy";
		case 1:
			return "Mine";
		case 2:
			return "Spire";
		case 3:
			return "Basis";
		default:
			return key.toString();
		}
	}

	/**
	 * stores the gateway awnser jsondata to an file
	 */
	public static void saveData(final String awn, final Date now) throws IOException
	{
		final File root = new File("data");
		if (!root.isDirectory())
			root.mkdirs();
		final SimpleDateFormat df = new SimpleDateFormat("yyMMdd_HHmmss");
		final File target = new File(root, df.format(now) + ".proj.txt");
		// args -> 0 -> container -> states -> X
		// 0 Botschaft, 1 Mine, 2 Spire, 3 Basis
		// tasks -> [] -> taskdef -> displayname
		// tasks -> [] -> buckets -> [] -> currentquantity/requiredquantity/requireditemdisplayname
		FileWriter fw = null;
		try
		{
			fw = new FileWriter(target);
			final JSONObject dat = new JSONObject(awn.substring(4));
			final JSONArray states = ((JSONObject) dat.getJSONArray("args").get(0)).getJSONObject("container").getJSONArray(
					"states");
			for (int i = 0; i < states.length(); i++)
			{
				fw.write("\n\nDepartment:" + i + "\n");
				final JSONArray tasks = ((JSONObject) states.get(i)).getJSONArray("tasks");
				for (int tasknr = 0; tasknr < tasks.length(); tasknr++)
				{
					final JSONObject task = (JSONObject) tasks.get(tasknr);
					if (task.getInt("active") == 0)
						continue;
					final String name = task.getJSONObject("taskdef").getString("displayname");
					fw.write("\nTask:" + name + "\n");

					final JSONArray buckets = task.getJSONArray("buckets");
					for (int bucketnr = 0; bucketnr < buckets.length(); bucketnr++)
					{
						final JSONObject bucket = (JSONObject) buckets.get(bucketnr);
						final String bucketname = bucket.getString("requireditemdisplayname");
						final int current = bucket.getInt("currentquantity");
						final int total = bucket.getInt("requiredquantity");
						fw.write(bucketname + ":" + current + "/" + total + "\n");
					}
				}
			}
		}
		finally
		{
			fw.close();
		}
	}
}
