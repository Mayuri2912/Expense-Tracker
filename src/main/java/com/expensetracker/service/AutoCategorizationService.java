package com.expensetracker.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class AutoCategorizationService {

    private static final Map<String, List<String>> CATEGORY_MAP = new LinkedHashMap<>();

    static {
        CATEGORY_MAP.put("Food & Dining", List.of("swiggy", "zomato", "starbucks", "mcdonalds", "kfc", "dominos", "cafe", "restaurant"));
        CATEGORY_MAP.put("Transportation", List.of("uber", "ola", "rapido", "metro", "petrol", "shell", "hpcl", "bpcl"));
        CATEGORY_MAP.put("Shopping", List.of("amazon", "flipkart", "myntra", "zara", "hm", "croma"));
        CATEGORY_MAP.put("Groceries", List.of("blinkit", "zepto", "instamart", "bigbasket", "dmart"));
        CATEGORY_MAP.put("Entertainment", List.of("netflix", "spotify", "bookmyshow", "pvr"));
        CATEGORY_MAP.put("Healthcare", List.of("apollo", "pharmeasy", "1mg", "medplus", "hospital"));
    }

    public String categorizeMerchant(String merchant) {
        if (merchant == null || merchant.trim().isEmpty()) {
            return "General Expense";
        }

        String lowerMerchant = merchant.toLowerCase();
        for (Map.Entry<String, List<String>> entry : CATEGORY_MAP.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lowerMerchant.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return "General Expense";
    }
}