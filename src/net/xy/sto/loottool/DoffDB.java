package net.xy.sto.loottool;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

public class DoffDB {
	File old = new File("resources/master_old.csv");
	File masta = new File("resources/master.csv");
	File newdoff = new File("resources/master_new.csv");
	File effects = new File("resources/effects.csv");
	File persistant = new File("resources/doff.dat");

	private Map<String, DoffData> db = new HashMap<>();
	private final Map<Integer, EffectData> edb = new HashMap<>();

	@SuppressWarnings("unchecked")
	public void load() {
		if (false && persistant.exists()) {
			ObjectInputStream oi = null;
			try {
				oi = new ObjectInputStream(new FileInputStream(persistant));
				db = (Map<String, DoffData>) oi.readObject();
			} catch (final Exception e) {
				e.printStackTrace();
			}
		} else
			try {
				loadFile();
				final ObjectOutputStream oo = new ObjectOutputStream(new FileOutputStream(persistant));
				oo.writeObject(db);
				oo.close();
			} catch (final IOException e) {
				e.printStackTrace();
			}

		try {
			loadEffects();
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	private void loadEffects() throws IOException {
		for (final File fi : new File[] { effects }) {
			final Reader in = new FileReader(fi);
			final Iterable<CSVRecord> records = CSVFormat.EXCEL.parse(in);

			for (final CSVRecord record : records) {
				final String code = record.get(2);
				final String area = record.get(3);
				final String desc = record.get(4);
				try {
					final int icode = Integer.parseInt(code);
					edb.put(icode, new EffectData(icode, area, desc));
				} catch (final NumberFormatException nfe) {
				}
			}
		}
	}

	private void loadFile() throws IOException {
		int count = 0;
		for (final File fi : new File[] { old, masta, newdoff }) {
			final Reader in = new FileReader(fi);
			final Iterable<CSVRecord> records = CSVFormat.EXCEL.parse(in);

			for (final CSVRecord record : records) {
				count += 1;
				if (count % 1000 == 0)
					System.out.println("Parsing file: " + count);
				final String name = record.get(0);
				if ("Name".equals(name))
					continue;

				final String depart = record.get(3);
				final String special = record.get(4);
				final String rarity = record.get(5);
				final String effect = record.get(7);
				db.put(name, new DoffData(name, special, depart, rarity, effect));
			}
		}
	}

	public static class DoffData implements Serializable {
		private static final long serialVersionUID = -4553190945253096943L;

		private final String name;
		private final String special;
		private final Integer effect;
		private final Rarity rarity;

		private final Department department;

		public DoffData(final String name, final String special, final String depart, final String rarity,
				final String effect) {
			this.name = name;
			this.special = special;
			if (!effect.isEmpty())
				this.effect = Integer.valueOf(effect);
			else
				this.effect = null;
			this.rarity = Rarity.valueFor(rarity);
			department = Department.valueOf(depart);
		}

		public Department getDepartment() {
			return department;
		}

		public String getName() {
			return name;
		}

		public String getSpecial() {
			return special;
		}

		public Integer getEffect() {
			return effect;
		}

		public Rarity getRarity() {
			return rarity;
		}
	}

	public static enum Department {
		Tactical, Security, Engineering, Operations, Science, Medical, Civilian;
	}

	public static enum Rarity {
		Common, Uncommon, Rare, Very_Rare, Ultra_Rare, Epic;

		public static Rarity valueFor(final String name) {
			try {
				return Enum.valueOf(Rarity.class, name);
			} catch (final IllegalArgumentException ia) {
				if ("Very Rare".equals(name))
					return Rarity.Very_Rare;
				if ("Ultra Rare".equals(name))
					return Rarity.Ultra_Rare;
				throw ia;
			}
		}
	}

	public static class EffectData {
		private final int code;
		private final Object area;
		private final String desc;

		public EffectData(final int code, final String area, final String desc) {
			this.code = code;
			this.area = "[SP]".equals(area) ? Area.Space : Area.Ground;
			this.desc = desc;
		}

		public int getCode() {
			return code;
		}

		public Object getArea() {
			return area;
		}

		public String getDesc() {
			return desc;
		}

		@Override
		public String toString() {
			return desc;
		}
	}

	public static enum Area {
		Space, Ground
	};

	public DoffData get(final String key) {
		return db.get(key);
	}

	public EffectData getEffect(final Integer code) {
		if (code == null)
			return null;
		return edb.get(code);
	}
}
