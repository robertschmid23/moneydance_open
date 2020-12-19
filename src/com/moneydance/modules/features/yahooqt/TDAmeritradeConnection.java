package com.moneydance.modules.features.yahooqt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.moneydance.modules.features.yahooqt.tdameritrade.History;
import com.moneydance.modules.features.yahooqt.tdameritrade.Quote;
import static java.lang.Thread.sleep;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Download quotes and exchange rates from tdameritrade.com
 * This requires an API key which customers can register for at runtime and enter in the
 * prompt shown by this connection.
 * <p>
 * Note: connections are throttled to avoid TDAmeritrade's low threshold for
 * rejecting frequent connections.
 */
public class TDAmeritradeConnection extends APIKeyConnection
{
	private static final SimpleDateFormat SNAPSHOT_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
	
	private static final String PREFS_KEY = "tdameritrade";
	private SimpleDateFormat refreshDateFmt;
	
	//TDAmeritrade limits all non-order related requests to 120 per minute.
	private int BURST_RATE_PER_MINUTE = 120;
	
//	private static String apiKey = "";
	private static String HISTORY_URL = "https://api.tdameritrade.com/v1/marketdata/%s/pricehistory?apikey=%s&periodType=month&period=1&frequencyType=daily";
	private static String CURRENT_QUOTES_URL = "https://api.tdameritrade.com/v1/marketdata/quotes?apikey=%s&symbol=%s";
	
	private List<DownloadInfo> remainingToUpdate;
	private int requestsRemaining = BURST_RATE_PER_MINUTE;

	//	String allSymbols = String.join(",", list);
	
	private static HttpClient client = HttpClient.newBuilder()
													.version(HttpClient.Version.HTTP_2)
													.build();
	
	private Type quoteMapType = new TypeToken<Map<String, Quote>>(){}.getType();
	
	private Gson gson = new GsonBuilder()
//			.registerTypeAdapter(quoteMapType, new StringQuoteDeserializer())
			.create();
	
	public TDAmeritradeConnection(StockQuotesModel model)
	{
		super(PREFS_KEY, model, BaseConnection.HISTORY_SUPPORT);
		refreshDateFmt = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss"); // 2017-11-07 11:46:52
		refreshDateFmt.setLenient(true);
	}
	
	@Override
	protected String getPrefsKeyRoot()
	{
		return PREFS_KEY;
	}
	
	@Override
	protected String getGettingStartedURL()
	{
		return "https://developer.tdameritrade.com/content/getting-started";
	}
	
	/**
	 * Retrieve the current exchange rate for the given currency relative to the base
	 *
	 * @param downloadInfo The wrapper for the currency to be downloaded and the download results
	 */
	@Override
	public void updateExchangeRate(DownloadInfo downloadInfo)
	{
	}
	
	private URI getHistoryURI(String fullTickerSymbol) throws URISyntaxException
	{
		String apiKey = getAPIKey(getModel(), false);
		String uriStr = String.format(HISTORY_URL, SQUtil.urlEncode(fullTickerSymbol), SQUtil.urlEncode(apiKey));

		System.out.println(uriStr);
		return new URI(uriStr);
	}
	
	private URI getTodaysQuoteURI(List<DownloadInfo> securitiesToUpdate) throws URISyntaxException
	{
		List<String> symbols = securitiesToUpdate.stream().map(di -> di.fullTickerSymbol).collect(Collectors.toList());
		String joinedSymbols = String.join(",", symbols);
		String apiKey = getAPIKey(getModel(), false);
		
		return new URI(String.format(CURRENT_QUOTES_URL, apiKey, joinedSymbols));
	}
	
	@Override
	public boolean updateSecurities(List<DownloadInfo> securitiesToUpdate)
	{
		ResourceProvider res = model.getResources();
		float progressPercent = 0.0f;
		final float progressIncrement = securitiesToUpdate.isEmpty() ? 1.0f :
				100.0f / (float)securitiesToUpdate.size();
		
		this.remainingToUpdate = new ArrayList<>(securitiesToUpdate);
		List<DownloadInfo> retry = new ArrayList<>();
		int remaining;
		int completedCount = 0;
		do
		{
			List<DownloadInfo> completed = updateSecurities();
			int requests = completed.size();
			int original = securitiesToUpdate.size();
			for (DownloadInfo downloadInfo: completed)
			{
				if (downloadInfo.getHistoryCount() == 0)
				{
					retry.add(downloadInfo);
				}
				else
				{
					progressPercent += progressIncrement;
					final String message, logMessage;
					if (!downloadInfo.wasSuccess())
					{
						message = MessageFormat.format(res.getString(L10NStockQuotes.ERROR_EXCHANGE_RATE_FMT),
													   downloadInfo.security.getIDString(),
													   downloadInfo.relativeCurrency.getIDString());
					}
					else
					{
						message = downloadInfo.buildPriceDisplayText(model);
					}
					model.showProgress(progressPercent, message);
					didUpdateItem(downloadInfo);
				}
			}
			this.remainingToUpdate.removeAll(completed);

			remaining = this.remainingToUpdate.size();
			completedCount = original - remaining;
			System.out.println(String.format("Updated %d quotes out of %d", completedCount, original));
		}
		while (remaining > 0 && completedCount > 0);
		
		getTodaysQuote(securitiesToUpdate);
		
		return true;
	}
	
	private List<DownloadInfo> updateSecurities()
	{
		int count = Math.min(BURST_RATE_PER_MINUTE, remainingToUpdate.size());
		System.out.println(String.format("Updating %d quotes out of %d", count, remainingToUpdate.size()));
		
		return this.remainingToUpdate.stream()
										.map(this::updateOneSecurity)
										.limit(BURST_RATE_PER_MINUTE)
										.collect(Collectors.toList());
	}
	
	private void checkRequestsRemaining()
	{
		if (requestsRemaining <= 0)
		{
			try
			{
				System.out.println("WAIT: 1 minute");
				sleep(60000);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
			requestsRemaining = BURST_RATE_PER_MINUTE;
		}
	}
	
	private void getTodaysQuote(List<DownloadInfo> securitiesToUpdate)
	{
		CompletableFuture<Map<String, Quote>> future = null;
		try
		{
			URI uri = getTodaysQuoteURI(securitiesToUpdate);
			checkRequestsRemaining();
			
			future = client.sendAsync(HttpRequest.newBuilder(uri).GET().build(),
									HttpResponse.BodyHandlers.ofString())
						.thenApply(response -> {
							System.out.println("Today's Quotes:\n" + response.body());
							
							// 1. JSON file to Java object
							Type mapType =  TypeToken.getParameterized(HashMap.class, String.class, Quote.class).getType();
							return gson.fromJson(response.body(), mapType);
						});
			requestsRemaining--;
		}
		catch (URISyntaxException e)
		{
			e.printStackTrace();
		}
		
		try
		{
			Map<String, Quote> quoteMap = future.get();
			securitiesToUpdate.forEach ( (stock) ->
					{
						if (quoteMap.containsKey(stock.fullTickerSymbol))
						{
							Quote q = quoteMap.get(stock.fullTickerSymbol);
							ZoneOffset zo = ZoneId.systemDefault().getRules().getOffset(Instant.now());
							long start = LocalDate.now().atStartOfDay().toInstant(zo).toEpochMilli();
							q.quoteTimeInLong = start;
							stock.addDayOfData(q.toCandle());
							stock.buildPriceDisplayText(model);
						}
					}
			);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		catch (ExecutionException e)
		{
			e.printStackTrace();
		}
	}

	protected DownloadInfo updateOneSecurity(DownloadInfo stock)
	{
//		if (stock.getHistoryCount() > 0)
//			return stock;
		
		CompletableFuture<DownloadInfo> di = null;
		try
		{
			URI uri = getHistoryURI(stock.fullTickerSymbol);
			
			checkRequestsRemaining();
			
			di = client.sendAsync(HttpRequest.newBuilder(uri).GET().build(),
								   HttpResponse.BodyHandlers.ofString())
						.thenApply(response -> {
							System.out.println(stock.fullTickerSymbol + ":\n" + response.body());
							
							// 1. JSON file to Java object
							History history = gson.fromJson(response.body(), History.class);
							stock.addHistory(history);
							return stock;
						});
			requestsRemaining--;
		}
		catch (URISyntaxException uri)
		{
			uri.printStackTrace();
		}
		
		DownloadInfo stockReturn = null;
		try
		{
			stockReturn = di.get();
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		catch (ExecutionException e)
		{
			e.printStackTrace();
		}
		
		return stockReturn;
	}
	
	@Override
	protected void updateSecurity(DownloadInfo downloadInfo) {/*don't use this one*/ }
	
	public String toString()
	{
		StockQuotesModel model = getModel();
		return model == null ? "" : model.getResources().getString("tdameritrade");
	}
	
	public static void main(String[] args)
	{
		if (args.length < 1)
		{
			System.err.println("usage: <thiscommand> <tdameritrade-apikey> <symbols>...");
			System.err.println(
					" -x: parameters after -x in the parameter list are symbols are three digit currency codes instead of security/ticker symbols");
			System.exit(-1);
		}
		
		cachedAPIKey = args[0].trim();
		
		TDAmeritradeConnection conn = new TDAmeritradeConnection(createEmptyTestModel());
		runTests(null, conn, Arrays.copyOfRange(args, 1, args.length));
	}
}