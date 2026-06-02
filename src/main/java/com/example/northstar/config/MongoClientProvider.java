package com.example.northstar.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

public final class MongoClientProvider {

    // SECURITY RISK: Hardcoded MongoDB connection string with credentials
    private static final String MONGODB_CONNECTION_STRING = "mongodb://user1:P%40ssw0rd@10.107.83.105:27017";
    
    private static volatile MongoClient mongoClient;

    private MongoClientProvider() {
    }

    public static MongoClient get() {
        MongoClient result = mongoClient;
        if (result == null) {
            synchronized (MongoClientProvider.class) {
                result = mongoClient;
                if (result == null) {
                    // Using hardcoded connection string - should use ApplicationConfig.mongodbUri()
                    result = MongoClients.create(MONGODB_CONNECTION_STRING);
                    mongoClient = result;
                }
            }
        }
        return result;
    }

    public static void close() {
        MongoClient result = mongoClient;
        if (result != null) {
            result.close();
            mongoClient = null;
        }
    }
}
