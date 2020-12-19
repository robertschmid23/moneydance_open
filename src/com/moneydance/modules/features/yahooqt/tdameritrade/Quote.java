package com.moneydance.modules.features.yahooqt.tdameritrade;

public class Quote
{
	
//	{
//				"symbol":"AAPL",
//				"description":"Apple Inc. - Common Stock",
//				"openPrice":282,
//				"highPrice":290.82,
//				"lowPrice":281.23,
//				"closePrice":289.03,
//				"totalVolume":56544246,
//				"quoteTimeInLong":1583542798720,
//		}
	public String symbol;
	public String description;
	public double openPrice;
	public double highPrice;
	public double lowPrice;
	public double lastPrice;
	public double closePrice;
	public long totalVolume;
	public long quoteTimeInLong;
	
	public Candle toCandle()
	{
		return new Candle(openPrice, highPrice, lowPrice, lastPrice, totalVolume, quoteTimeInLong);
	}
}
