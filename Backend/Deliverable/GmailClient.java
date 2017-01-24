
/******************************************************************************
 *  Date: Dec 12 2016
 *  Class: GmailClient.java
 *  Purpose: E-mail client tailored for Gmail
 *  Author: Christopher Chan
 ******************************************************************************/

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.Properties;
import java.util.TimeZone;

import javax.mail.BodyPart;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.FlagTerm;

public class GmailClient {
	private Session smtp_session;
	private Properties imap_props;
	private PasswordAuthentication auth;
	
	public GmailClient(String username, String password) throws MessagingException {
		Properties smtp_props = new Properties();
		smtp_props.put("mail.smtp.host", "smtp.gmail.com");
		smtp_props.put("mail.smtp.starttls.enable", "true");
		smtp_props.put("mail.smtp.auth", "true");		
		smtp_props.put("mail.smtp.port", "587");
		smtp_session = Session.getInstance(smtp_props,
				new javax.mail.Authenticator() {
				protected PasswordAuthentication getPasswordAuthentication() {
						return auth;
					}
				});
		
		imap_props = new Properties();
	    imap_props.put("mail.imap.host", "imap.gmail.com");
	    imap_props.put("mail.imap.port", "993");
	    imap_props.put("mail.imap.starttls.enable", "true");
	    auth = new PasswordAuthentication(username,password);
	}
	
	public void SendEmail(String recipient, String subject, String msgText) {
	
		try {
			Message message = new MimeMessage(smtp_session);
			message.setRecipients(Message.RecipientType.TO,
					InternetAddress.parse(recipient));
			message.setSubject(subject);
			message.setText(msgText);
			Transport.send(message);
			System.out.print(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)+"--");
			System.out.println("Sent to "+recipient+":"+subject+":"+msgText);
		} catch (MessagingException e) {
			throw new RuntimeException(e);
		}
	}
	
	public String[] ReadEmail() throws Exception {
		LinkedList<String> rcvdEmails = new LinkedList<String>();
		Session session = Session.getInstance(imap_props);
		Store imap_store = session.getStore("imaps");
		imap_store.connect(session.getProperty("mail.imap.host"),auth.getUserName(),auth.getPassword());
		
		Folder emailFolder = imap_store.getFolder("INBOX");
		emailFolder.open(Folder.READ_WRITE);
		
		Message[] messages = emailFolder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
		for (int i = 0, n = messages.length; i < n; i++) {
	         Message message = messages[i];
	         DateFormat df = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy");
		     df.setTimeZone(TimeZone.getTimeZone("GMT"));
		     DateTimeFormatter formatter = DateTimeFormatter.ofPattern("E MMM dd HH:mm:ss z yyyy");
		     LocalDateTime localDateTime = LocalDateTime.parse(df.format(message.getSentDate()),formatter);
		     String dateSent = localDateTime.format(DateTimeFormatter.ISO_DATE_TIME);
		     String subject = message.getSubject();
		     String sender = message.getFrom()[0].toString();
		     //System.out.println("Body Text:"+getTextFromMessage(message));
		     String emailRecvd = sender+"="+dateSent+"="+subject;
		     System.out.print(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)+"--");
		     System.out.println("Received "+emailRecvd);
		     rcvdEmails.add(emailRecvd);
		     message.setFlag(Flags.Flag.SEEN, true);
		}
		emailFolder.close(true);
		imap_store.close();
		return (String[])rcvdEmails.toArray(new String[rcvdEmails.size()]);
	}
	
		@SuppressWarnings("unused")
		private String getTextFromMessage(Message message) throws Exception {
		    String result = "";
		    if (message.isMimeType("text/plain")) {
		        result = message.getContent().toString();
		    } else if (message.isMimeType("multipart/*")) {
		        MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
		        result = getTextFromMimeMultipart(mimeMultipart);
		    }
		    return result;
		}

		private String getTextFromMimeMultipart(
		        MimeMultipart mimeMultipart) throws Exception{
		    String result = "";
		    int count = mimeMultipart.getCount();
		    for (int i = 0; i < count; i++) {
		        BodyPart bodyPart = mimeMultipart.getBodyPart(i);
		        if (bodyPart.isMimeType("text/plain")) {
		            result = result + "\n" + bodyPart.getContent();
		            break; // without break same text appears twice in my tests
		        } else if (bodyPart.isMimeType("text/html")) {
		            String html = (String) bodyPart.getContent();
		            result = result + "\n" + org.jsoup.Jsoup.parse(html).text();
		        } else if (bodyPart.getContent() instanceof MimeMultipart){
		            result = result + getTextFromMimeMultipart((MimeMultipart)bodyPart.getContent());
		        }
		    }
		    return result;
		}	
}
