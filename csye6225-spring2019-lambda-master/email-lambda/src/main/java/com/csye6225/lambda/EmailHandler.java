package com.csye6225.lambda;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;

public class EmailHandler implements RequestHandler<Map<String, Object>, String> {
	static final String FROM = "no-reply@csye6225-spring2019-" + System.getenv("AWS_DOMAIN_NAME") + ".me";

	static final String CONFIGSET = "ConfigSet";

	static final String SUBJECT = "Password reset for CSYE6225-SPRING2019-webapp";

	static final String TEXTBODY = "This email was sent through Amazon SES " + "using the AWS SDK for Java.";

	public String handleRequest(Map<String, Object> inputMap, Context context) {
		String token = "Hello";
		List<Object> records = (List<Object>) inputMap.get("Records");
		Map<Object, Object> objMap = (Map<Object, Object>) records.get(0);
		Map<Object, Object> sns = (Map<Object, Object>) objMap.get("Sns");
		String input = (String) sns.get("Message");
		AmazonDynamoDB dbclient = AmazonDynamoDBClientBuilder.defaultClient();
		DynamoDB dynamoDb = new DynamoDB(dbclient);

		Table table = dynamoDb.getTable("csye6225");
		QuerySpec spec = new QuerySpec().withKeyConditionExpression("email = :v_email")
				.withFilterExpression("expiration > :v_exp").withValueMap(new ValueMap().withString(":v_email", input)
						.withNumber(":v_exp", System.currentTimeMillis() / 1000L));
		ItemCollection<QueryOutcome> items = table.query(spec);
		Iterator<Item> iterator = items.iterator();
		System.out.println("before while");
		Item item = null;
		if (iterator.hasNext()) {
			item = iterator.next();
			token = item.getString("token");
		} else {
			token = UUID.randomUUID().toString();
			item = new Item().withPrimaryKey("email", input).withString("token", token).withNumber("expiration",
					(System.currentTimeMillis() + (20 * 60 * 1000)) / 1000L);
			table.putItem(item);
			String htmlBody = "Click on the following link to reset your password <br /> "
					+ "<a href = '#'>http://csye6225-spring2019-" + System.getenv("AWS_DOMAIN_NAME")
					+ ".me/reset?email=" + input + "&token=" + token + "</a>";

			AmazonSimpleEmailService emailClient = AmazonSimpleEmailServiceClientBuilder.defaultClient();
			SendEmailRequest request = new SendEmailRequest().withDestination(new Destination().withToAddresses(input))
					.withMessage(new Message()
							.withBody(new Body().withHtml(new Content().withCharset("UTF-8").withData(htmlBody))
									.withText(new Content().withCharset("UTF-8").withData(TEXTBODY)))
							.withSubject(new Content().withCharset("UTF-8").withData(SUBJECT)))
					.withSource(FROM);
			emailClient.sendEmail(request);
		}
		System.out.println(input);
		return token;
	}

}
