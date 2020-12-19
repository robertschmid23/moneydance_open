package com.moneydance.modules.features.yahooqt.tdameritrade;

public class Candle
{
	public double open;
	public double high;
	public double low;
	public double close;
	public long volume;
	public long datetime;
	
	public Candle()
	{
	}
	
	public Candle(double open, double high, double low, double close, long volume, long datetime)
	{
		this.open = open;
		this.high = high;
		this.low = low;
		this.close = close;
		this.volume = volume;
		this.datetime = datetime;
	}
}
//	{
//		"candles": [
//		{
//			"open": 320.25,
//				"high": 323.33,
//				"low": 317.5188,
//				"close": 318.31,
//				"volume": 36634380,
//				"datetime": 1579845600000
//		}]
//}
