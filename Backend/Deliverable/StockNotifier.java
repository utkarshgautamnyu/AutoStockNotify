/******************************************************************************
 *  Date: Dec 12 2016
 *  Class: StockNotifier.java
 *  Purpose: Main Executive: StockNotifier
 *  Author: Christopher Chan
 ******************************************************************************/

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Scanner;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.SubscribeRequest;

public class StockNotifier {
	 public static void main(String[] args) throws Exception {
		 GmailClient mailClient = new GmailClient("","");

		String AWSAccessKeyId =" ";
		String AWSSecretKey =" ";
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
		DynamoDBInterface db_interface = new DynamoDBInterface(credentials);
		AmazonSNSClient snsClient = new AmazonSNSClient(credentials);		                           
		snsClient.setRegion(Region.getRegion(Regions.US_WEST_2));

		 long elapsedSec = 0;
		 long stockWaitSec = 2;
		 while (true) {
			 long startTotalSec = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
			 checkUserSpecs(mailClient,db_interface);
			 if (elapsedSec>stockWaitSec) {
				 updateStockPrices(db_interface);
				 elapsedSec = 0;
				 if (stockWaitSec==2)
					 stockWaitSec = 1;
				 else
					 stockWaitSec = 2;
			 }
			 sendNotifications(mailClient, db_interface,snsClient);
			 long stopTotalSec = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
			 long transpiredSec = stopTotalSec-startTotalSec;
			 long sleepTimeSec = 1-transpiredSec;
			 if (sleepTimeSec>0)
				 Thread.sleep(sleepTimeSec*1000);
			 elapsedSec+=transpiredSec;
		 }
	 }

	public static String setupSNS(AmazonSNSClient snsClient, String email, DynamoDBInterface db_interface) {
		String[] fields = email.split("@");
		CreateTopicRequest createTopicRequest = new CreateTopicRequest(fields[0]);
		CreateTopicResult createTopicResult = snsClient.createTopic(createTopicRequest);
		
		String topicArn = createTopicResult.getTopicArn();
		SubscribeRequest subRequest = new SubscribeRequest(topicArn, "email", email);
		snsClient.subscribe(subRequest);
		
		db_interface.insertUpdateSNS(email, topicArn);
		
		return topicArn;
	}
	
	 public static void checkUserSpecs(GmailClient mailClient,DynamoDBInterface db_interface) throws Exception {
		 
		 String[] rcvdEmails = new String[]{};
		 try {
			 rcvdEmails = mailClient.ReadEmail();
		 } catch (Exception e) {
			 System.out.println(e.getMessage());
			 rcvdEmails = new String[]{};
		 }
		 for (int i=0;i<rcvdEmails.length;i++) {
			 String[] fields= rcvdEmails[i].split("=");
			 String email = fields[0];
			 if (fields[0].contains("<")) {
				 email = fields[0].substring(fields[0].indexOf("<")+1,fields[0].indexOf(">"));
			 }
			 String date = fields[1];
			 String subject = fields[2];
			 long userID = db_interface.getUserID(email);
			 if (userID<1) {
				 System.out.println(email+" is not a user");
			 } else {
				 System.out.println("User "+email+" successfully authenticated, processing email Date:"+date+" Subject:"+subject);
				 if (subject.startsWith("GETCONFIG")) {
					 	 String[] result = db_interface.getConfiguration(email);
						 mailClient.SendEmail(email,result[0],result[1]);
				 } else if (subject.startsWith("CREATE")&&email.equals("chris0chan@gmail.com")) {
					 String reply = "SUCCESS ON "+subject;
					 try {
						 String [] subjectFields = subject.split("-");
						 String new_username = subjectFields[1];
						 String new_email = subjectFields[2];
						 db_interface.insertUser(new_username, new_email);
					 } catch (Exception e) {
						 reply = e.getMessage();
					 }
					 mailClient.SendEmail(email,"CREATE Status",reply);
				 } else if (fields[2].startsWith("UPDATE")) {
					 String reply = "SUCCESS ON "+subject;
					 try {
						 String [] subjectFields = subject.split("-");
						 String symbol = subjectFields[1];
						 double startPrice = Double.parseDouble(subjectFields[2]);
						 double stopPrice = Double.parseDouble(subjectFields[3]);
						 db_interface.insertUpdateNotify(email, symbol,startPrice,stopPrice);
					 } catch (Exception e) {
						 reply = e.getMessage();
					 }
					 mailClient.SendEmail(email,"INSERT Status",reply);
				 } else if (fields[2].startsWith("DELETE")) {
					 String reply = "SUCCESS ON "+subject;
					 try {
						 String [] subjectFields = subject.split("-");
						 String symbol = subjectFields[1];
						 db_interface.deleteNotify(email, symbol);
					 } catch (Exception e) {
						 reply = e.getMessage();
					 }
					 mailClient.SendEmail(email,"DELETE Status",reply);
				 }
			 }
		 }
	 }

	 private static void updateStockPrices(DynamoDBInterface db_interface) throws Exception {
		try {
			for (String symbol : db_interface.getNotifyStocks()) {
				 StockQuote quote = new StockQuote(symbol);
				 if (quote.valid)
				 {
					 db_interface.insertUpdateStock(symbol, "-", "-", "-", quote.priceOf(), quote.dateOf());
				 } else {
					 System.out.println("Invalid quote for "+symbol);
				 }
				
			 }
		 } catch (Exception e) {
			 System.out.println("StockNotifier::updateStockPrices ERROR:"+e.getMessage());
		 }
	 }
	
	 private static void sendNotifications(GmailClient mailClient,DynamoDBInterface db_interface,AmazonSNSClient snsClient )
				throws Exception {
		for (String notification : db_interface.getNotifications()) {
			 String[] fields = notification.split("=");
			 String email = fields[0];
			 String arn = db_interface.getARN(email);
			 if (arn==""){
				 arn = StockNotifier.setupSNS(snsClient, email, db_interface);
			 }else {
				 snsClient.publish(new PublishRequest(arn,fields[2]));
			 }
			 mailClient.SendEmail(email, fields[2], fields[3]);
			 db_interface.insertUpdateNotify(email, fields[1], -1, -1);
		 }
	 }
	
}
