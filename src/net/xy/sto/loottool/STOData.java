package net.xy.sto.loottool;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class STOData {

	public static class AbstractData {
		private String text;

		public void setText(final String text) {
			this.text = text;
		}

		public String getText() {
			return text;
		}
	}

	public static class ItemReceived extends AbstractData {
		private static Pattern pt = Pattern.compile("Items acquired: (.*) x (.*)");
		private static Pattern pt2 = Pattern.compile("Item acquired: (.*)");
		private static Pattern pt3 = Pattern.compile("You received a new title choice: (.*)");
		private String item;
		private int amount;

		@Override
		public void setText(final String text) {
			super.setText(text);
			// System.out.println(getItem() + ": " + getAmount());
		}

		public String getItem() {
			chackParse();
			return item;
		}

		public int getAmount() {
			chackParse();
			return amount;
		}

		private void chackParse() {
			if (item != null)
				return;

			Matcher matcher;
			if ((matcher = pt.matcher(getText())).matches()) {
				item = matcher.group(1);
				amount = Integer.valueOf(matcher.group(2));
			} else if ((matcher = pt2.matcher(getText())).matches()) {
				item = matcher.group(1);
				amount = 1;
			} else if ((matcher = pt3.matcher(getText())).matches()) {
				item = matcher.group(1);
				amount = 1;
			} else
				throw new IllegalArgumentException("No pattern match [" + pt + "][" + getText() + "]");
		}
	}

	public static class NumericReceived extends AbstractData {
		private static Pattern pt = Pattern.compile("You received ([0-9,]+) (.*)");
		private String numeric;
		private int amount;

		@Override
		public void setText(final String text) {
			super.setText(text);
			// System.out.println(getNumeric() + ": " + getAmount());
		}

		public String getNumeric() {
			chackParse();
			return numeric;
		}

		public int getAmount() {
			chackParse();
			return amount;
		}

		private void chackParse() {
			if (numeric != null)
				return;

			final Matcher matcher = pt.matcher(getText());
			if (!matcher.matches())
				throw new IllegalArgumentException("No pattern match [" + pt + "][" + getText() + "]");
			numeric = matcher.group(2);
			final NumberFormat nf = NumberFormat.getInstance(Locale.US);
			try {
				amount = nf.parse(matcher.group(1)).intValue();
			} catch (final ParseException e) {
				e.printStackTrace();
			}
		}
	}
}
