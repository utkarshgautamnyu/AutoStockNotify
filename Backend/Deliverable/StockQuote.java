import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

/******************************************************************************
 *  Date: Dec 12 2016
 *  Class: StockQuote.java
 *  Purpose: Retrieve stock information
 *  Author: Christopher Chan
 ******************************************************************************/

public class StockQuote {
	String html;
	public boolean valid = true;
	
	public StockQuote(String symbol) throws InterruptedException {
        html = getHTML(symbol);
        if (html.contains("<title></title>")||html.length()==0) 
        	valid = false;
	}

	private String getHTML(String symbol) throws InterruptedException {
		String html ="";
		try {
			String line;
	        BufferedReader r = new BufferedReader(new InputStreamReader(new URL("https://finance.google.com/finance/info?client=ig&q="+symbol).openStream()));
	        while ((line = r.readLine())!=null) {
	            html+=line;
	        }
		} catch (Exception e) {
			System.out.println("StockQuote Exception:"+e.getMessage());
			//Thread.sleep(2000);
			//return getHTML(symbol);
		}
		return html;
	}
	
    public double priceOf() {
        int p     = html.indexOf("l_fix", 0);      
        int from  = html.indexOf(":", p);          
        int to    = html.indexOf(",", from);
        String priceStr = html.substring(from + 3, to-2);
        double price = Double.parseDouble(priceStr.replaceAll(",", "")); 
        return price;
    }

    public String nameOf() {
    	int p     = html.indexOf("t", 0);
        int from  = html.indexOf(":", p);
        int to    = html.indexOf(",", from);
        String name= html.substring(from + 3, to-2);
        return name;	
    }

    public String dateOf() {
    	int p     = html.indexOf("lt_dts", 0);
        int from  = html.indexOf(":", p);  
        int to    = html.indexOf(",", from);
        String date = html.substring(from + 3, to-2);
        return date;
    }
}
