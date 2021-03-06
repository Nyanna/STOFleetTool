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
import java.util.Map.Entry;
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
public class DonationTable
{
	/**
	 * dateformat file pattern vor donation data
	 */
	public static final SimpleDateFormat DF_FLEET_DONATIONS = new SimpleDateFormat("yyMMdd_HHmmss");
	/**
	 * data store, Nick -> Date -> slot -> donation
	 */
	private final Map<String, Map<Date, Map<Integer, Integer>>> store = new TreeMap<String, Map<Date, Map<Integer, Integer>>>();
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
	public DonationTable(final File root) throws ParseException, IOException
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
				return pathname.getName().endsWith(".txt");
			}
		}))
		{
			final Date fd = DF_FLEET_DONATIONS.parse(file.getName().substring(0, file.getName().length() - 4));
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
			String line;
			while ((line = buf.readLine()) != null)
			{
				final String[] cols = line.split(",");
				if (cols.length != 4)
					continue;

				String nick = cols[2].trim();
				final String[] nickParts = nick.split("@");
				nick = "@" + nickParts[1] + "->" + nickParts[0];
				Map<Date, Map<Integer, Integer>> dates = store.get(nick);
				if (dates == null)
				{
					dates = new TreeMap<Date, Map<Integer, Integer>>();
					store.put(nick, dates);
				}

				Map<Integer, Integer> don = dates.get(fd);
				if (don == null)
				{
					don = new TreeMap<Integer, Integer>();
					dates.put(fd, don);
				}
				don.put(Integer.valueOf(cols[0].trim()), Integer.valueOf(cols[3].trim()));
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

		final TreeSet<Date> dates = new TreeSet<Date>();
		for (final Entry<String, Map<Date, Map<Integer, Integer>>> entry : store.entrySet())
			for (final Entry<Date, Map<Integer, Integer>> date : entry.getValue().entrySet())
				dates.add(date.getKey());
		final List<Date> datesrev = new ArrayList<Date>(dates);
		Collections.reverse(datesrev);

		sb.append("<th>");
		final DateFormat df = new SimpleDateFormat("MM.dd HH:mm");
		for (final Date hd : datesrev)
		{
			sb.append("<td class=\"date\">");
			sb.append(df.format(hd));
			sb.append("</td>");
		}
		sb.append("</th>");

		for (final Entry<String, Map<Date, Map<Integer, Integer>>> entry : store.entrySet())
		{
			final StringBuilder tr = new StringBuilder();
			tr.append("<tr>");
			tr.append("<td class=\"nick\">" + entry.getKey() + "</td>");
			Integer lastSum = null;
			Integer[] lastDepSums = new Integer[4];
			boolean changedAny = false;
			// for (final Entry<Date, Map<Integer, Integer>> date : entry.getValue().entrySet())
			final List<StringBuilder> tds = new ArrayList<StringBuilder>();
			for (final Date hd : dates)
			{
				final StringBuilder td = new StringBuilder();
				int sum = 0;
				final Integer[] depSums = new Integer[4];
				final StringBuilder title = new StringBuilder();
				final Map<Integer, Integer> dons = entry.getValue().get(hd);

				if (dons != null && !dons.isEmpty())
				{
					title.append("Departments:\n");
					for (final Entry<Integer, Integer> depart : dons.entrySet())
					{
						sum += depart.getValue();
						final Integer depId = depart.getKey();
						title.append(getName(depId));
						title.append(":");
						title.append(depart.getValue());
						title.append(", ");
						depSums[depId] = depart.getValue();
					}
				}
				if (title.length() > 0)
				{
					title.setLength(title.length() - 2);
					title.append(", Total:" + sum + "");
				}
				final boolean changed = lastSum != null && sum != lastSum;
				changedAny |= changed;
				if (changed)
				{
					title.append("\n\nDifference:\n");
					final int tdiff = sum - lastSum;
					for (int i = 0; i < lastDepSums.length; i++)
						if (lastDepSums[i] != null && lastDepSums[i] != depSums[i])
						{
							final int ddiff = depSums[i] - lastDepSums[i];
							title.append("" + getName(i) + ":" + (ddiff > 0 ? "+" : "") + ddiff + ", ");
						}
					title.append(", Total:" + (tdiff > 0 ? "+" : "") + tdiff + "");
				}
				td.append("<td class=\"credits\" title=\"" + title.toString() + "\"");
				if (changed)
					td.append(" style=\"background:#FF0000;font-weight:bold;\"");
				td.append(" \">");
				td.append(sum);
				td.append("</td>");
				lastSum = sum;
				lastDepSums = depSums;
				tds.add(td);
			}
			Collections.reverse(tds);
			for (final StringBuilder td : tds)
				tr.append(td);
			tr.append("</tr>\n");
			if (props.getProperty("onlyChanged", "0").equals("0") || changedAny)
				sb.append(tr);
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
		final File target = new File(root, df.format(now) + ".txt");
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
}
