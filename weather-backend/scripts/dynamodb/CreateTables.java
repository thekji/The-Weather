import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.Tag;

public class CreateTables {

    // read aws region fron environment, if region is missing -> use us-east-1 by default
    private static final String REGION = environment("AWS_REGION", "us-east-1");
    private static final String USERS_TABLE = environment("USERS_TABLE", "Users");

    // aws sdk client to talk to dynamodb        
    // create an aws dynamo client using the configured region
    // aws credentials are automatically loaded by the aws sdk from environment variables 
    public static void main(String[] args) {

        // create the dynamodb client used to communicate with aws
        try (DynamoDbClient dynamoDb = DynamoDbClient.builder()
                .region(Region.of(REGION))
                .build()) {

            // create the users table if it does not already exist
            createUsersTable(dynamoDb);
        }

        // printed only when table created/validated successfully
        System.out.println(USERS_TABLE + " is ready in " + REGION);
    }

    // define the users table schema
    // set email as partition key 
    private static void createUsersTable(DynamoDbClient dynamoDb) {
        CreateTableRequest request = baseRequest(USERS_TABLE)
                .attributeDefinitions(attribute("email"))
                .keySchema(key("email", KeyType.HASH))
                .build();

        // create the table if missing
        // otherwise verify the existing schema
        createIfMissing(dynamoDb, request);
    }

    // configuration shared accross dynamodb tables
    private static CreateTableRequest.Builder baseRequest(String tableName) {
        return CreateTableRequest.builder()
                .tableName(tableName)
                .billingMode(BillingMode.PAY_PER_REQUEST)

                // add a tag -> can easily be identified as belonging to theweather
                .tags(Tag.builder().key("Project").value("TheWeather").build());
    }

    // helper method for creating a dynamodb attribute definition
    // all current key attributes use string type
    private static AttributeDefinition attribute(String name) {
        return AttributeDefinition.builder()
                .attributeName(name)
                .attributeType(ScalarAttributeType.S)
                .build();
    }

    // helper method for defining table keys.
    // keytype.hash  = partition key
    // keytype.range = sort key
    private static KeySchemaElement key(String name, KeyType type) {
        return KeySchemaElement.builder()
                .attributeName(name)
                .keyType(type)
                .build();
    }

    private static void createIfMissing(DynamoDbClient dynamoDb, CreateTableRequest request) {
        String tableName = request.tableName();
        
        // check if the table exist so that they will wait till available then validate schema
        if (tableExists(dynamoDb, tableName)) {
            waitForTable(dynamoDb, tableName);
            validateUsersTable(dynamoDb);
            System.out.println(tableName + " already exists with the expected schema");
            return;
        }

        // if missing table, they will create a table then wait till available and validate schema
        System.out.println("Creating " + tableName + "...");
        dynamoDb.createTable(request);
        waitForTable(dynamoDb, tableName);
        validateUsersTable(dynamoDb);
        System.out.println("Created " + tableName);
    }

    // aws sdk waiter repeatedly checks the table status
    // to wait for dynamodb table creation to complete
    private static void waitForTable(DynamoDbClient dynamoDb, String tableName) {
        try (var waiter = dynamoDb.waiter()) {
            waiter.waitUntilTableExists(builder -> builder.tableName(tableName));
        }
    }

    // validate the existing users table
    private static void validateUsersTable(DynamoDbClient dynamoDb) {
        // check for the current table definition
        DescribeTableResponse response =
                dynamoDb.describeTable(builder -> builder.tableName(USERS_TABLE));

        // check if table has "email" as key, as partion key, as string
        boolean hasEmailKey = response.table().keySchema().size() == 1
                && hasKey(response.table().keySchema(), "email", KeyType.HASH)
                && hasStringAttribute(response, "email");

        if (!hasEmailKey) {
            throw new IllegalStateException(
                    "Existing " + USERS_TABLE
                            + " must have email as its String partition key");
        }
    }

    // check whether a key schema contains a specific key
    // checks whether email is the partition key
    private static boolean hasKey(
            java.util.List<KeySchemaElement> keys,
            String attributeName,
            KeyType keyType) {
        return keys.stream().anyMatch(key ->
                attributeName.equals(key.attributeName()) && keyType == key.keyType());
    }

    // check whether an attribute exists in the table's attribute definitions and has string type
    private static boolean hasStringAttribute(
            DescribeTableResponse response,
            String attributeName) {
        return response.table().attributeDefinitions().stream().anyMatch(attribute ->
                attributeName.equals(attribute.attributeName())
                        && attribute.attributeType() == ScalarAttributeType.S);
    }

    // check whether a dynamodb table exists
    private static boolean tableExists(DynamoDbClient dynamoDb, String tableName) {
        try {
            dynamoDb.describeTable(builder -> builder.tableName(tableName));
            return true;
        } catch (ResourceNotFoundException exception) {
            return false;
        }
    }

    // read an environment variable
    private static String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
