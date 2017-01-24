import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.UpdateItemOutcome;
import com.amazonaws.services.dynamodbv2.document.spec.DeleteItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.sns.AmazonSNSClient;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Scanner;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;

public class DynamoDBInterface {

	AWSCredentials credentials;
	AmazonDynamoDBClient dbClient;
	DynamoDB dynamoDB;
	Table users_table;
	Table sns_table;
	Table notify_table;
	Table stock_table;
	public DynamoDBInterface(AWSCredentials credentials) {
		this.credentials = credentials;
		String endPoint = "https://rds.us-west-2.amazonaws.com";
		Region region = Region.getRegion(Regions.US_WEST_2);
		
		//dbClient = new AmazonDynamoDBClient(new ProfileCredentialsProvider());
		dbClient = new AmazonDynamoDBClient(credentials);
		dbClient.withRegion(region);
		//dbClient.setEndpoint("http://localhost:8000");
		
		dynamoDB = new DynamoDB(dbClient);
		users_table = dynamoDB.getTable("USERS_TABLE");
		sns_table = dynamoDB.getTable("SNS_TABLE");
		notify_table = dynamoDB.getTable("NOTIFY_TABLE");
		stock_table = dynamoDB.getTable("STOCK_TABLE");
		
		System.out.println("Open DynamoDB interface");
    }
	
	public boolean isStock(String symbol) {
		QuerySpec spec = new QuerySpec()
        	    .withKeyConditionExpression("symbol = :v_symbol")
        	    .withValueMap(new ValueMap()
        	        .withString(":v_symbol", symbol));
		ItemCollection<QueryOutcome> items = stock_table.query(spec);
		Iterator<Item> iterator = items.iterator();
    	return iterator.hasNext();
	}

	public boolean isSNS(String email) {
		QuerySpec spec = new QuerySpec()
        	    .withKeyConditionExpression("email = :v_email")
        	    .withValueMap(new ValueMap()
        	        .withString(":v_email", email));
		ItemCollection<QueryOutcome> items = sns_table.query(spec);
		Iterator<Item> iterator = items.iterator();
    	return iterator.hasNext();
	}
	
	public boolean isNotify(String email, String symbol) {
		Map<String, AttributeValue> expressionAttributeValues = 
			    new HashMap<String, AttributeValue>();
		Long userID = getUserID(email);
		expressionAttributeValues.put(":v_id", new AttributeValue().withN(userID.toString()));
		expressionAttributeValues.put(":v_symbol", new AttributeValue().withS(symbol));
		        
		ScanRequest scanRequest = new ScanRequest()
		    .withTableName("NOTIFY_TABLE")
		    .withFilterExpression("id = :v_id AND symbol = :v_symbol")
		    .withExpressionAttributeValues(expressionAttributeValues);
		
		ScanResult result = dbClient.scan(scanRequest);
		return result.getCount()>0;
	}
	
	public String getEmail(long userID) {
		QuerySpec spec = new QuerySpec()
        	    .withKeyConditionExpression("id = :v_id")
        	    .withValueMap(new ValueMap()
        	        .withNumber(":v_id", userID));
		ItemCollection<QueryOutcome> items = users_table.query(spec);
		Iterator<Item> iterator = items.iterator();
    	if (iterator.hasNext()) {
    	    return iterator.next().getString("email");
    	} else {
    		return "";
    	}
	}
	
	public long getUserID(String email) {
		long userID = -999;
		Map<String, AttributeValue> expressionAttributeValues = 
			    new HashMap<String, AttributeValue>();
			expressionAttributeValues.put(":email", new AttributeValue().withS(email)); 
			        
			ScanRequest scanRequest = new ScanRequest()
			    .withTableName("USERS_TABLE")
			    .withFilterExpression("email = :email")
			    .withExpressionAttributeValues(expressionAttributeValues);
			ScanResult result = dbClient.scan(scanRequest);
			if (result.getItems().size()>0) {
				Map<String, AttributeValue> item = result.getItems().get(0);
				userID = Long.parseLong(item.get("id").getN());
			}
			return userID;
	}
	
	public long getLastUserID() {
		ScanRequest scanRequest = new ScanRequest("USERS_TABLE");
		 ScanResult result = dbClient.scan(scanRequest);
		 return result.getCount();
	}
	
	public long getNotifyID(String email, String symbol) {
		Map<String, AttributeValue> expressionAttributeValues = 
			    new HashMap<String, AttributeValue>();
		expressionAttributeValues.put(":v_symbol", new AttributeValue().withS(symbol));
		Long userID = getUserID(email);
		expressionAttributeValues.put(":v_id", new AttributeValue().withN(userID.toString()));
		        
		ScanRequest scanRequest = new ScanRequest()
		    .withTableName("NOTIFY_TABLE")
		    .withFilterExpression("symbol= :v_symbol AND id = :v_id")
		    .withExpressionAttributeValues(expressionAttributeValues);
		ScanResult result = dbClient.scan(scanRequest);
		long notifyID = -999;
		if (result!=null) 
			notifyID = Long.parseLong(result.getItems().get(0).get("notify_id").getN());
		return notifyID;
	}
	
	public long getLastNotifyID() {
		ScanRequest scanRequest = new ScanRequest("NOTIFY_TABLE");
		 ScanResult result = dbClient.scan(scanRequest);
		 return result.getCount();
	}
	
	public String getLastNotified(String email, String symbol) {
		QuerySpec spec = new QuerySpec()
        	    .withKeyConditionExpression("id = :v_id and begins_with(symbol, :v_symbol)")
        	    .withValueMap(new ValueMap()
        	    .withLong(":v_id", getUserID(email))
        	    .withString("v_symbol",symbol));
		ItemCollection<QueryOutcome> items = notify_table.query(spec);
		Iterator<Item> iterator = items.iterator();
    	if (iterator.hasNext()) {
    	    return iterator.next().getString("lastnotified");
    	} else {
    		return "";
    	}
	}

	public String getARN(String email) {
		String arn = "";
		Item item = sns_table.getItem("email",email);
		if (item!=null)
			arn = item.getString("arn");
		return arn;
	}
	
	public String[] getNotifyStocks() {
		LinkedList<String> symbols = new LinkedList<String>();
		ScanRequest scanRequest = new ScanRequest()
    		    .withTableName("NOTIFY_TABLE");
		ScanResult result = dbClient.scan(scanRequest);
		for (Map<String, AttributeValue> item : result.getItems()){
			String symbol = item.get("symbol").getS();
			if (!symbols.contains(symbol))
				symbols.add(symbol);
		}
		return (String[])symbols.toArray(new String[symbols.size()]);
	}

	public Map<String, AttributeValue> getItem(String symbol) {
		Map<String, AttributeValue> expressionAttributeValues = 
			    new HashMap<String, AttributeValue>();
		expressionAttributeValues.put(":v_symbol", new AttributeValue().withS(symbol)); 
		        
		ScanRequest scanRequest = new ScanRequest()
		    .withTableName("STOCK_TABLE")
		    .withFilterExpression("symbol= :v_symbol")
		    .withExpressionAttributeValues(expressionAttributeValues);
		ScanResult result = dbClient.scan(scanRequest);
		if (result!=null) 
			return result.getItems().get(0);
		else
			return null;
	}
	
	public String[] getConfiguration(String email) {
		Map<String, AttributeValue> expressionAttributeValues = 
			    new HashMap<String, AttributeValue>();
		Long userID = getUserID(email);
		expressionAttributeValues.put(":v_id", new AttributeValue().withN(userID.toString()));
		ScanRequest scanRequest = new ScanRequest()
			    .withTableName("NOTIFY_TABLE")
			    .withFilterExpression("id= :v_id")
			    .withExpressionAttributeValues(expressionAttributeValues);
			ScanResult result = dbClient.scan(scanRequest);
			
		String subject = "Stock Notification Configuration";
		String body = "";
		for (Map<String, AttributeValue> item : result.getItems()){
    		String symbol = item.get("symbol").getS();
			double start = Double.parseDouble(item.get("startprice").getN());
			double stop = Double.parseDouble(item.get("stopprice").getN());
			String lastNotified = item.get("lastnotified").getS();
			Map<String, AttributeValue> stockItem = getItem(symbol);
			double price = Double.parseDouble(stockItem.get("price").getN());
			String lastUpdated = stockItem.get("lastupdated").getS();
			String stockInfo = symbol+"@$"+price+" on "+lastUpdated+" for $"+start+"-"+"$"+stop+" last notified on "+lastNotified;
			body+=stockInfo+"\n";
    	}
		return new String[]{subject,body};
	}
	
	public String[] getNotifications()  {
		LinkedList<String> notifies = new LinkedList<String>();
		ScanRequest scanRequest = new ScanRequest()
				.withTableName("NOTIFY_TABLE");
		ScanResult result = dbClient.scan(scanRequest);
		for (Map<String, AttributeValue> item : result.getItems()){
    		String symbol = item.get("symbol").getS();
			double start = Double.parseDouble(item.get("startprice").getN());
			double stop = Double.parseDouble(item.get("stopprice").getN());
			String lastNotified = item.get("lastnotified").getS();
			Map<String, AttributeValue> stockItem = getItem(symbol);
			double price = Double.parseDouble(stockItem.get("price").getN());
			String lastUpdated = stockItem.get("lastupdated").getS();
			String url = stockItem.get("url").getS();
			String email = getEmail(Long.parseLong(item.get("id").getN()));
			LocalDateTime lastNotifiedLDT = LocalDateTime.parse(lastNotified,DateTimeFormatter.ISO_DATE_TIME);
			long totalSec = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)-lastNotifiedLDT.toEpochSecond(ZoneOffset.UTC);
			if (start<=price&&price<=stop&&totalSec>=60) {
				String subject = symbol+"@"+price+" at "+lastUpdated;
				String body = url;
				notifies.add(email+"="+symbol+"="+subject+"="+body);
			}
		}
		return (String[])notifies.toArray(new String[notifies.size()]);
	}
	
	public void insertUser(String username, String email) {
			long nextID = getLastUserID()+1;
		  PutItemOutcome outcome = users_table.putItem(new Item()
	                .withPrimaryKey("id", nextID)
	                .withString("email", email)
	                .withString("username",username));
		  System.out.println("PutItem for users_table succeeded:\n" + outcome.getPutItemResult());
	}
	
	public void insertUpdateNotify(String email,String symbol,double startPrice, double stopPrice) {
		long userID = getUserID(email);
		long nextNotifyID = getLastNotifyID()+1;
		boolean isNotify = isNotify(email,symbol);
		LocalDateTime localDateTime = LocalDateTime.now();
		String currentTime = localDateTime.format(DateTimeFormatter.ISO_DATE_TIME);
		if (!isNotify) {
	        notify_table.putItem(new Item()
	                .withPrimaryKey("notify_id", nextNotifyID)
	                .withDouble("id", userID)
	                .withString("symbol", symbol)
	                .withDouble("startprice", startPrice)
	                .withDouble("stopprice", stopPrice)
	                .withString("lastnotified",currentTime));
	        System.out.println("PutItem for notify_table succeeded:\n" + notify_table.getItem("notify_id",nextNotifyID).toJSONPretty());
		} else if (startPrice>0 && stopPrice>0) {
			UpdateItemSpec updateItemSpec = new UpdateItemSpec()
		            .withPrimaryKey(new PrimaryKey("notify_id", getNotifyID(email,symbol)))
		            .withUpdateExpression("set symbol = :symbol, startprice = :startprice, stopprice = :stopprice")
		            .withReturnValues(ReturnValue.UPDATED_NEW)
		            .withValueMap(new ValueMap()
		            		.withString(":symbol",symbol)
		            		.withNumber(":startprice",startPrice)
		            		.withNumber(":stopprice",stopPrice));
			UpdateItemOutcome outcome =  notify_table.updateItem(updateItemSpec);
			System.out.println(currentTime+"--UpdateItem for notify_table succeeded for "+email+"("+symbol+"):\n" + outcome.getItem().toJSONPretty());
		} else {
			UpdateItemSpec updateItemSpec = new UpdateItemSpec()
		            .withPrimaryKey(new PrimaryKey("notify_id", getNotifyID(email,symbol)))
		            .withUpdateExpression("set symbol = :symbol, lastnotified = :lastnotified")
		            .withReturnValues(ReturnValue.UPDATED_NEW)
		            .withValueMap(new ValueMap()
		            		.withString(":symbol",symbol)
		            		.withString(":lastnotified",currentTime));
			UpdateItemOutcome outcome =  notify_table.updateItem(updateItemSpec);
			System.out.println(currentTime+"--UpdateItem for notify_table succeeded for "+email+"("+symbol+"):\n" + outcome.getItem().toJSONPretty());
		}
	}
	
	public void deleteUser(String email) throws Exception {
		Long userID = getUserID(email);
		if (userID>0) {
	        Map<String, AttributeValue> expressionAttributeValues = new HashMap<String, AttributeValue>();
			expressionAttributeValues.put(":v_id", new AttributeValue().withN(userID.toString()));
			ScanRequest scanRequest = new ScanRequest()
			    .withTableName("NOTIFY_TABLE")
			    .withFilterExpression("id = :v_id")
			    .withExpressionAttributeValues(expressionAttributeValues);
			ScanResult result = dbClient.scan(scanRequest);
			for (Map<String, AttributeValue> item : result.getItems()){
				 deleteNotify(email,item.get("symbol").getS());
			}
			
			DeleteItemSpec deleteItemSpec = new DeleteItemSpec()
	        		.withPrimaryKey(new PrimaryKey("id", getUserID(email)));
	        users_table.deleteItem(deleteItemSpec);
		}
	}
	
	public void deleteNotify(String email,String symbol) throws Exception {
		DeleteItemSpec deleteItemSpec = new DeleteItemSpec()
        		.withPrimaryKey(new PrimaryKey("notify_id", getNotifyID(email,symbol)));
        notify_table.deleteItem(deleteItemSpec);
	}
	
	public void deleteStock(String symbol) throws Exception {
		DeleteItemSpec deleteItemSpec = new DeleteItemSpec()
        		.withPrimaryKey(new PrimaryKey("symbol", symbol));
        stock_table.deleteItem(deleteItemSpec);
	}
	
	public void deleteSNS(String email) throws Exception {
		DeleteItemSpec deleteItemSpec = new DeleteItemSpec()
        		.withPrimaryKey(new PrimaryKey("email", email));
        stock_table.deleteItem(deleteItemSpec);
	}
	
	public void insertUpdateSNS(String email, String arn) {
		LocalDateTime localDateTime = LocalDateTime.now();
		String currentTime = localDateTime.format(DateTimeFormatter.ISO_DATE_TIME);
		if (!isSNS(email)) {
		sns_table.putItem(new Item()
					.withPrimaryKey("email", email)
				    .withString("arn", arn));
			System.out.println(currentTime+"--PutItem for sns_table succeeded for ("+email+"):\n" + sns_table.getItem("email",email).toJSONPretty());
		} else {
			UpdateItemSpec updateItemSpec = new UpdateItemSpec()
		            .withPrimaryKey(new PrimaryKey("email", email))
		            .withUpdateExpression("set arn=:arn")
		            .withReturnValues(ReturnValue.UPDATED_NEW)
		            .withValueMap(new ValueMap()
		            		.withString(":arn",arn));
			UpdateItemOutcome outcome =  sns_table.updateItem(updateItemSpec);
			System.out.println(currentTime+"--UpdateItem for sns_table succeeded for ("+email+"):\n" + outcome.getItem().toJSONPretty());		
		}
	}
	
	public void insertUpdateStock(String symbol, String name, String url, String market, double price, String lastupdated) {
		LocalDateTime localDateTime = LocalDateTime.now();
		String currentTime = localDateTime.format(DateTimeFormatter.ISO_DATE_TIME);
		if (!isStock(symbol)) {
			stock_table.putItem(new Item()
					.withPrimaryKey("symbol", symbol)
				    .withString("name", name)
				    .withString("url", url)
				    .withString("market", market)
				    .withDouble("price", price)
				    .withString("lastupdated",lastupdated));
			System.out.println(currentTime+"--PutItem for stock_table succeeded for ("+symbol+"):\n" + stock_table.getItem("symbol",symbol).toJSONPretty());
		} else {
			UpdateItemSpec updateItemSpec = new UpdateItemSpec()
		            .withPrimaryKey(new PrimaryKey("symbol", symbol))
		            .withUpdateExpression("set price=:price,lastupdated=:lastupdated")
		            .withReturnValues(ReturnValue.UPDATED_NEW)
		            .withValueMap(new ValueMap()
		            		.withNumber(":price",price)
		            		.withString(":lastupdated",lastupdated));
			UpdateItemOutcome outcome =  stock_table.updateItem(updateItemSpec);
			System.out.println(currentTime+"--UpdateItem for stock_table succeeded for ("+symbol+"):\n" + outcome.getItem().toJSONPretty());
		}
	}
	
	public static void main(String[] args) throws Exception { 
		//Credentials
		String AWSAccessKeyId ="";
		String AWSSecretKey ="";
		Scanner scanner; 
		if (AWSAccessKeyId.length()<1){
			scanner = new Scanner (System.in);
			System.out.println("AWSAcessKeyId:");
			AWSAccessKeyId = scanner.nextLine();
		}
		if (AWSSecretKey.length()<1){
			scanner = new Scanner (System.in);
			System.out.println("AWSSecretKey:");
			AWSSecretKey = scanner.nextLine();
		}
		
		AWSCredentials credentials = new BasicAWSCredentials(AWSAccessKeyId, AWSSecretKey);
		DynamoDBInterface dbInterface = new DynamoDBInterface(credentials);
		AmazonSNSClient snsClient = new AmazonSNSClient(credentials);		                           
		snsClient.setRegion(Region.getRegion(Regions.US_WEST_2));
		
		//dbInterface.deleteUser("chris0chan@gmail.com");
//		dbInterface.deleteTable("USERS_TABLE");
//		dbInterface.deleteTable("SNS_TABLE");
//		dbInterface.deleteTable("NOTIFY_TABLE");
//		dbInterface.deleteTable("STOCK_TABLE");
//		dbInterface.createDefaultTables();
		//dbInterface.populateStockTable();
		
		String arn = "arn:aws:sns:us-west-2:389626654357:MyNewTopic";
		String email = "chris0chan@gmail.com";
		dbInterface.insertUser("chris", "chris0chan@gmail.com");
		dbInterface.insertUpdateNotify("chris0chan@gmail.com", "AAPL", 110, 150);
		dbInterface.insertUpdateNotify("chris0chan@gmail.com", "AAPL", 112, 150);
		arn = StockNotifier.setupSNS(snsClient, email, dbInterface);
		LocalDateTime localDateTime = LocalDateTime.now();
		String currentTime = localDateTime.format(DateTimeFormatter.ISO_DATE_TIME);
		dbInterface.insertUpdateStock("AAPL","Apple","www.apple.com","NASDAQ",112,currentTime);
		for (String lin : dbInterface.getConfiguration("chris0chan@gmail.com"))
			System.out.println(lin);
		for (String symbol : dbInterface.getNotifyStocks()) {
			System.out.println(symbol);
		}
		for (String line : dbInterface.getNotifications()) {
			System.out.println("Test:"+line);
		}
//		dbInterface.deleteTable("USERS_TABLE");
//		dbInterface.deleteTable("SNS_TABLE");
//		dbInterface.deleteTable("NOTIFY_TABLE");
//		dbInterface.deleteTable("STOCK_TABLE");
	}

	private void createDefaultTables() {
		DynamoDBInterface.createTable(dynamoDB,"USERS_TABLE",10L,5L,"id","N");
		DynamoDBInterface.createTable(dynamoDB,"SNS_TABLE",10L,5L,"email","S");
		DynamoDBInterface.createTable(dynamoDB,"NOTIFY_TABLE",10L,5L,"notify_id","N");
		DynamoDBInterface.createTable(dynamoDB,"STOCK_TABLE",10L,5L,"symbol","S");
	}
	
	 private void deleteTable(String tableName) {
	        Table table = dynamoDB.getTable(tableName);
	        try {
	            System.out.println("Issuing DeleteTable request for " + tableName);
	            table.delete();
	            System.out.println("Waiting for " + tableName
	                + " to be deleted...this may take a while...");
	            table.waitForDelete();

	        } catch (Exception e) {
	            System.err.println("DeleteTable request failed for " + tableName);
	            System.err.println(e.getMessage());
	        }
	 }
	 
    private static void createTable(DynamoDB dynamoDB,
        String tableName, long readCapacityUnits, long writeCapacityUnits, 
        String partitionKeyName, String partitionKeyType) {

        createTable(dynamoDB,tableName, readCapacityUnits, writeCapacityUnits,
            partitionKeyName, partitionKeyType, null, null);
    }
    
    private static void createTable(DynamoDB dynamoDB,
            String tableName, long readCapacityUnits, long writeCapacityUnits, 
            String partitionKeyName, String partitionKeyType, 
            String sortKeyName, String sortKeyType) {

            try {

                ArrayList<KeySchemaElement> keySchema = new ArrayList<KeySchemaElement>();
                keySchema.add(new KeySchemaElement()
                    .withAttributeName(partitionKeyName)
                    .withKeyType(KeyType.HASH)); //Partition key
                
                ArrayList<AttributeDefinition> attributeDefinitions = new ArrayList<AttributeDefinition>();
                attributeDefinitions.add(new AttributeDefinition()
                    .withAttributeName(partitionKeyName)
                    .withAttributeType(partitionKeyType));

                if (sortKeyName != null) {
                    keySchema.add(new KeySchemaElement()
                        .withAttributeName(sortKeyName)
                        .withKeyType(KeyType.RANGE)); //Sort key
                    attributeDefinitions.add(new AttributeDefinition()
                        .withAttributeName(sortKeyName)
                        .withAttributeType(sortKeyType));
                }

                CreateTableRequest request = new CreateTableRequest()
                        .withTableName(tableName)
                        .withKeySchema(keySchema)
                        .withProvisionedThroughput( new ProvisionedThroughput()
                            .withReadCapacityUnits(readCapacityUnits)
                            .withWriteCapacityUnits(writeCapacityUnits));

                request.setAttributeDefinitions(attributeDefinitions);

                System.out.println("Issuing CreateTable request for " + tableName);
                Table table = dynamoDB.createTable(request);
                System.out.println("Waiting for " + tableName
                    + " to be created...this may take a while...");
                table.waitForActive();

            } catch (Exception e) {
                System.err.println("CreateTable request failed for " + tableName);
                System.err.println(e.getMessage());
            }
        }
    
    public void populateStockTable() throws IOException {
		String[] marketNames = new String[3];
		marketNames[0] = "AMEX";
		marketNames[1] = "NASDAQ";
		marketNames[2] = "NYSE";
		for (int i=0;i<1;i++) {
			String marketName = marketNames[i];
    		System.out.println("MarketName:"+marketName);
    		BufferedReader in = new BufferedReader(new FileReader(marketName.toLowerCase()+".csv"));
    		String s;
    		marketName = "'"+marketName+"'";
    		in.readLine();
    		while((s = in.readLine()) != null){
		        String[] var = s.split("\",");
		        var[0]+="\"";
		        var[1]+="\"";
		        var[7]+="\"";
		        System.out.println(var[0]+":"+var[1]+":"+var[7]+":"+marketName);
		        this.insertUpdateStock(var[0], var[1], var[7], marketName, -1, "NULL");
		        break;
    		}
    		in.close();
		}
    }
}
