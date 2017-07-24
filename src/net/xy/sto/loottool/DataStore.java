package net.xy.sto.loottool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import net.xy.sto.loottool.DoffDB.DoffData;
import net.xy.sto.loottool.STOData.AbstractData;
import net.xy.sto.loottool.STOData.ItemReceived;
import net.xy.sto.loottool.STOData.NumericReceived;

public class DataStore {
	private final ConcurrentHashMap<AbstractData, AbstractData> store = new ConcurrentHashMap<>();

	public void addData(final AbstractData data) {
		store.put(data, data);
	}

	public String printSummary(final DoffDB doffdb) {
		final StringBuilder out = new StringBuilder();
		final TreeMap<String, Integer> items = new TreeMap<>();
		final TreeMap<String, Integer> stats = new TreeMap<>();

		for (final AbstractData elem : store.values())
			if (elem instanceof NumericReceived) {
				final NumericReceived nr = (NumericReceived) elem;
				Integer val = stats.get(nr.getNumeric());
				if (val == null)
					stats.put(nr.getNumeric(), val = nr.getAmount());
				else
					stats.put(nr.getNumeric(), val + nr.getAmount());
			} else if (elem instanceof ItemReceived) {
				final ItemReceived ir = (ItemReceived) elem;
				Integer val = items.get(ir.getItem());
				if (val == null)
					items.put(ir.getItem(), val = ir.getAmount());
				else
					items.put(ir.getItem(), val + ir.getAmount());
			}

		final ArrayList<DoffData> doffs = new ArrayList<>();
		for (final Entry<String, Integer> entry : items.entrySet()) {
			final DoffData doff = doffdb.get(entry.getKey());

			if (doff != null)
				doffs.add(doff);
			else
				out.append(entry.getKey() + ": " + entry.getValue()).append("<br />\n");
		}

		Collections.sort(doffs, new Comparator<DoffData>() {
			@Override
			public int compare(final DoffData o1, final DoffData o2) {
				int res = Integer.compare(o1.getDepartment().ordinal(), o2.getDepartment().ordinal());

				if (res == 0)
					res = o1.getSpecial().compareTo(o2.getSpecial());
				return res;
			}
		});

		if (doffs.size() > 0) {
			// out.append("Found doffs: " + doffs.size()).append("<br />\n");
			out.append("<table>\n");
			out.append("<tr><td>Name</td><td>Specialization</td><td>Effect</td></tr>\n");
			for (final DoffData doff : doffs) {
				out.append("<tr>\n");
				out.append("<td> " + doff.getName() + "</td><td>" + doff.getSpecial() + "</td><td>"
						+ doffdb.getEffect(doff.getEffect())).append("</td>\n");
				out.append("</tr>\n");
			}
			out.append("</table>\n");
		}
		return out.toString();
	}
}
